import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.HdrHistogram.Histogram;

/**
 * Scheduler benchmark harness. Uses only the public scheduler API, so the same class runs against
 * every scheduler jar (main, PR variants, virtual-thread prototype).
 *
 * <p>Usage: java SchedBench scenario=<name> key=value... ; prints one JSON line with the results.
 */
public final class SchedBench {

  static final Map<String, String> ARGS = new HashMap<>();
  static volatile boolean measuring;
  static volatile boolean running = true;
  static final Map<String, Object> OUT = new LinkedHashMap<>();
  static volatile Boolean actorOnVirtualThread;
  static volatile String phase = "init";

  static String arg(final String key, final String def) {
    return ARGS.getOrDefault(key, def);
  }

  static int intArg(final String key, final int def) {
    return Integer.parseInt(arg(key, String.valueOf(def)));
  }

  static Histogram newHistogram() {
    return new Histogram(TimeUnit.SECONDS.toNanos(300), 3);
  }

  /** Burns CPU for about {@code nanos}. */
  static long busy(final long nanos) {
    if (nanos <= 0) {
      return 0;
    }
    final long end = System.nanoTime() + nanos;
    long x = 0;
    while (System.nanoTime() < end) {
      x += x * 31 + 7;
    }
    return x;
  }

  static void noteThread() {
    if (actorOnVirtualThread == null) {
      actorOnVirtualThread = Thread.currentThread().isVirtual();
    }
  }

  // ---------------------------------------------------------------------- native blocking

  static final MethodHandle POLL;

  static {
    final Linker linker = Linker.nativeLinker();
    POLL =
        linker.downcallHandle(
            linker.defaultLookup().find("poll").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
  }

  /** Blocks the carrier/OS thread in a native call, like a long RocksDB/JNI call or mmap fault. */
  static void blockNative(final long millis) {
    final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    long left;
    while ((left = end - System.nanoTime()) > 0) {
      try {
        final int ignored =
            (int) POLL.invokeExact(MemorySegment.NULL, 0L, (int) Math.max(1, left / 1_000_000));
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }
  }

  static void block(final String kind, final long millis) {
    switch (kind) {
      case "native" -> blockNative(millis);
      case "sleep" -> {
        try {
          Thread.sleep(millis);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      case "spin" -> busy(TimeUnit.MILLISECONDS.toNanos(millis));
      default -> throw new IllegalArgumentException(kind);
    }
  }

  // ---------------------------------------------------------------------- actors

  /** Receives requests, burns {@code workNs}, records submit-to-done latency. */
  static final class Worker extends Actor {
    final Histogram hist = newHistogram();
    final long workNs;

    Worker(final String name, final long workNs) {
      super(name);
      this.workNs = workNs;
    }

    void submit(final long submittedAt) {
      actor.run(() -> handle(submittedAt));
    }

    void handle(final long submittedAt) {
      noteThread();
      busy(workNs);
      if (measuring) {
        hist.recordValue(System.nanoTime() - submittedAt);
      }
    }
  }

  /** ActorControl#runBlocking, if the scheduler under test has it (simplified scheduler spike). */
  static final Method RUN_BLOCKING = findRunBlocking();

  static Method findRunBlocking() {
    try {
      return ActorControl.class.getMethod("runBlocking", Callable.class);
    } catch (final NoSuchMethodException e) {
      return null;
    }
  }

  /** Plain actor whose control is exposed to the harness. */
  static final class Plain extends Actor {
    Plain(final String name) {
      super(name);
    }

    /** Runs the blocking call via actor.runBlocking and continues on the actor when it is done. */
    @SuppressWarnings("unchecked")
    void offload(final Runnable blocking) {
      try {
        final Callable<Object> callable =
            () -> {
              blocking.run();
              return null;
            };
        final ActorFuture<Object> f = (ActorFuture<Object>) RUN_BLOCKING.invoke(actor, callable);
        actor.runOnCompletion(f, (v, e) -> {});
      } catch (final ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // ---------------------------------------------------------------------- load generation

  /**
   * Open-loop load generator on a platform thread (like a gateway/network thread): wakes up every
   * {@code tickMicros} and submits every request whose intended time has passed.
   */
  static Thread startClient(
      final String name, final double ratePerSec, final List<Worker> targets, final AtomicLong sent) {
    final Thread t =
        Thread.ofPlatform()
            .name(name)
            .daemon()
            .start(
                () -> {
                  final long interval = (long) (1e9 / ratePerSec);
                  final long tick = TimeUnit.MICROSECONDS.toNanos(200);
                  long next = System.nanoTime();
                  final ThreadLocalRandom rnd = ThreadLocalRandom.current();
                  while (running) {
                    final long now = System.nanoTime();
                    while (next <= now) {
                      targets.get(rnd.nextInt(targets.size())).submit(System.nanoTime());
                      sent.incrementAndGet();
                      next += interval;
                    }
                    LockSupport.parkNanos(Math.min(tick, next - now));
                  }
                });
    return t;
  }

  // ---------------------------------------------------------------------- CPU accounting

  static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

  static Map<Long, String> threadNames() {
    final Map<Long, String> m = new HashMap<>();
    for (final var info : THREADS.getThreadInfo(THREADS.getAllThreadIds())) {
      if (info != null) {
        m.put(info.getThreadId(), info.getThreadName());
      }
    }
    return m;
  }

  static Map<Long, Long> threadCpu() {
    final Map<Long, Long> m = new HashMap<>();
    for (final long id : THREADS.getAllThreadIds()) {
      final long cpu = THREADS.getThreadCpuTime(id);
      if (cpu >= 0) {
        m.put(id, cpu);
      }
    }
    return m;
  }

  static boolean isSchedulerThread(final String name) {
    return name.startsWith("zb-") || name.startsWith("ForkJoinPool") || name.contains("Unparker");
  }

  static long processCpu() {
    return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
        .getProcessCpuTime();
  }

  /** cgroup v2 cpu.stat of this JVM (CFS quota accounting), or an empty map if unavailable. */
  static Map<String, Long> cgroupCpuStat() {
    final Map<String, Long> m = new HashMap<>();
    try {
      final String line =
          java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/cgroup")).get(0);
      final String path = line.substring(line.lastIndexOf(':') + 1);
      for (final String l :
          java.nio.file.Files.readAllLines(
              java.nio.file.Path.of("/sys/fs/cgroup" + path, "cpu.stat"))) {
        final String[] kv = l.split(" ");
        m.put(kv[0], Long.parseLong(kv[1]));
      }
    } catch (final Exception e) {
      // not available
    }
    return m;
  }

  static final class CpuWindow {
    final long startWall = System.nanoTime();
    final long startProc = processCpu();
    final Map<Long, Long> startThreads = threadCpu();
    final Map<String, Long> startCg = cgroupCpuStat();

    void finish() {
      final double wall = System.nanoTime() - startWall;
      final Map<String, Long> endCg = cgroupCpuStat();
      if (startCg.containsKey("nr_periods") && endCg.containsKey("nr_periods")) {
        final long periods = endCg.get("nr_periods") - startCg.get("nr_periods");
        final long throttled = endCg.get("nr_throttled") - startCg.get("nr_throttled");
        final long throttledUs = endCg.get("throttled_usec") - startCg.get("throttled_usec");
        OUT.put("cfs_periods", periods);
        OUT.put("cfs_throttled_pct", round2(100.0 * throttled / Math.max(1, periods)));
        OUT.put("cfs_throttled_ms_per_s", round2(throttledUs / 1000.0 / (wall / 1e9)));
      }
      OUT.put("cores_total", round2((processCpu() - startProc) / wall));
      final Map<Long, Long> end = threadCpu();
      final Map<Long, String> names = threadNames();
      long sched = 0;
      for (final var e : end.entrySet()) {
        final String name = names.getOrDefault(e.getKey(), "?");
        if (isSchedulerThread(name)) {
          sched += e.getValue() - startThreads.getOrDefault(e.getKey(), 0L);
        }
      }
      OUT.put("cores_sched", round2(sched / wall));
      int actors = 0, io = 0, blocking = 0;
      for (final String name : names.values()) {
        actors += name.startsWith("zb-actors") ? 1 : 0;
        io += name.startsWith("zb-fs-workers") ? 1 : 0;
        blocking += name.startsWith("zb-blocking") ? 1 : 0;
      }
      OUT.put("threads_actors", actors);
      OUT.put("threads_io", io);
      OUT.put("threads_blocking", blocking);
    }
  }

  // ---------------------------------------------------------------------- output

  static double round2(final double v) {
    return Math.round(v * 100) / 100.0;
  }

  static void putLatency(final String prefix, final Histogram h) {
    OUT.put(prefix + "_count", h.getTotalCount());
    OUT.put(prefix + "_p50_us", round2(h.getValueAtPercentile(50) / 1e3));
    OUT.put(prefix + "_p90_us", round2(h.getValueAtPercentile(90) / 1e3));
    OUT.put(prefix + "_p99_us", round2(h.getValueAtPercentile(99) / 1e3));
    OUT.put(prefix + "_p999_us", round2(h.getValueAtPercentile(99.9) / 1e3));
    OUT.put(prefix + "_max_us", round2(h.getMaxValue() / 1e3));
  }

  static Histogram merge(final List<Worker> workers) {
    final Histogram h = newHistogram();
    for (final Worker w : workers) {
      h.add(w.hist);
    }
    return h;
  }

  static String json() {
    final StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (final var e : OUT.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"').append(e.getKey()).append("\":");
      final Object v = e.getValue();
      if (v instanceof Number || v instanceof Boolean) {
        sb.append(v);
      } else {
        sb.append('"').append(v).append('"');
      }
    }
    return sb.append('}').toString();
  }

  // ---------------------------------------------------------------------- scenarios

  static List<Worker> submitWorkers(
      final ActorScheduler scheduler,
      final String prefix,
      final int n,
      final long workNs,
      final SchedulingHints hints) {
    final List<Worker> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      final Worker w = new Worker(prefix + i, workNs);
      scheduler.submitActor(w, hints).join();
      list.add(w);
    }
    return list;
  }

  static final AtomicLong PAIR_ROUND_TRIPS = new AtomicLong();
  static final List<Histogram> PAIR_HISTS = new ArrayList<>();

  /**
   * Starts P pairs of CPU actors that bounce one message until the run ends; each hop optionally
   * burns hopWorkNs. All actors are started before any pair begins bouncing.
   */
  static void startPairs(final ActorScheduler scheduler, final int pairs, final long hopWorkNs) {
    final List<Plain[]> actors = new ArrayList<>();
    for (int p = 0; p < pairs; p++) {
      phase = "starting pair " + p;
      final Plain a = new Plain("ping" + p);
      final Plain b = new Plain("pong" + p);
      scheduler.submitActor(a, SchedulingHints.cpuBound()).join();
      scheduler.submitActor(b, SchedulingHints.cpuBound()).join();
      actors.add(new Plain[] {a, b});
    }
    phase = "starting chains";
    for (final Plain[] pair : actors) {
      final Plain a = pair[0];
      final Plain b = pair[1];
      final Histogram h = newHistogram();
      PAIR_HISTS.add(h);
      final Runnable[] ping = new Runnable[1];
      ping[0] =
          () -> {
            noteThread();
            final long t0 = System.nanoTime();
            b.run(
                () -> {
                  busy(hopWorkNs);
                  a.run(
                      () -> {
                        busy(hopWorkNs);
                        if (measuring) {
                          h.recordValue(System.nanoTime() - t0);
                          PAIR_ROUND_TRIPS.incrementAndGet();
                        }
                        if (running) {
                          ping[0].run();
                        }
                      });
                });
          };
      a.run(ping[0]);
    }
  }

  static Histogram mergedPairs() {
    final Histogram all = newHistogram();
    PAIR_HISTS.forEach(all::add);
    return all;
  }

  /** P pairs of CPU actors bouncing one message; measures the actor-to-actor hop. */
  static void pingpong(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    startPairs(scheduler, intArg("pairs", 1), intArg("hopWorkNs", 0));
    phase = "warmup";
    Thread.sleep(warmupMs);
    phase = "measure";
    final CpuWindow cpu = new CpuWindow();
    measuring = true;
    Thread.sleep(durationMs);
    measuring = false;
    cpu.finish();
    running = false;
    OUT.put("throughput_per_s", Math.round(PAIR_ROUND_TRIPS.get() * 1000.0 / durationMs));
    putLatency("rtt", mergedPairs());
  }

  /**
   * Saturation fairness: P actor pairs bounce messages until the run ends (each hop burns
   * hopWorkNs), while an external client sends requests at a low rate to separate CPU actors.
   * Measures whether the external requests are served at all, and how late.
   */
  static void fairness(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    final List<Worker> workers =
        submitWorkers(
            scheduler, "ext", intArg("actors", 8), intArg("workNs", 5000), SchedulingHints.cpuBound());
    startPairs(scheduler, intArg("pairs", 12), intArg("hopWorkNs", 1000));
    final AtomicLong sent = new AtomicLong();
    startClient("bench-client-0", Double.parseDouble(arg("rate", "10000")), workers, sent);
    phase = "warmup";
    Thread.sleep(warmupMs);
    phase = "measure";
    final CpuWindow cpu = new CpuWindow();
    final long sentAtStart = sent.get();
    measuring = true;
    Thread.sleep(durationMs);
    measuring = false;
    cpu.finish();
    final long sentInWindow = sent.get() - sentAtStart;
    running = false;
    final Histogram ext = merge(workers);
    OUT.put("throughput_per_s", Math.round(PAIR_ROUND_TRIPS.get() * 1000.0 / durationMs));
    putLatency("ext", ext);
    OUT.put("ext_done_ratio", round2(ext.getTotalCount() / (double) Math.max(1, sentInWindow)));
    putLatency("rtt", mergedPairs());
  }

  /** One coordinator fans a request out to N workers and waits for all replies, round by round. */
  static void fanout(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    final int n = intArg("workers", 256);
    final long workNs = intArg("workNs", 2000);
    final Plain coord = new Plain("coord");
    scheduler.submitActor(coord, SchedulingHints.cpuBound()).join();
    final List<Plain> workers = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      final Plain w = new Plain("fan" + i);
      scheduler.submitActor(w, SchedulingHints.cpuBound()).join();
      workers.add(w);
    }
    final Histogram rounds = newHistogram();
    final AtomicLong roundCount = new AtomicLong();
    final Runnable[] round = new Runnable[1];
    final int[] replies = new int[1];
    final long[] roundStart = new long[1];
    final Runnable onReply =
        () -> {
          if (++replies[0] == n) {
            if (measuring) {
              rounds.recordValue(System.nanoTime() - roundStart[0]);
              roundCount.incrementAndGet();
            }
            if (running) {
              round[0].run();
            }
          }
        };
    round[0] =
        () -> {
          noteThread();
          replies[0] = 0;
          roundStart[0] = System.nanoTime();
          for (final Plain w : workers) {
            w.run(
                () -> {
                  busy(workNs);
                  coord.run(onReply);
                });
          }
        };
    coord.run(round[0]);
    phase = "warmup";
    Thread.sleep(warmupMs);
    phase = "measure";
    final CpuWindow cpu = new CpuWindow();
    measuring = true;
    Thread.sleep(durationMs);
    measuring = false;
    cpu.finish();
    running = false;
    OUT.put("throughput_per_s", Math.round(roundCount.get() * n * 1000.0 / durationMs));
    putLatency("round", rounds);
  }

  /** External (non-actor) threads submit requests at a fixed rate to CPU actors. */
  static void ingress(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    final List<Worker> workers =
        submitWorkers(
            scheduler, "cpu", intArg("actors", 32), intArg("workNs", 5000), SchedulingHints.cpuBound());
    final AtomicLong sent = new AtomicLong();
    final int clients = intArg("clients", 2);
    final double rate = Double.parseDouble(arg("rate", "50000"));
    for (int c = 0; c < clients; c++) {
      startClient("bench-client-" + c, rate / clients, workers, sent);
    }
    phase = "warmup";
    Thread.sleep(warmupMs);
    phase = "measure";
    final CpuWindow cpu = new CpuWindow();
    measuring = true;
    Thread.sleep(durationMs);
    measuring = false;
    cpu.finish();
    running = false;
    final Histogram h = merge(workers);
    OUT.put("throughput_per_s", Math.round(h.getTotalCount() * 1000.0 / durationMs));
    putLatency("lat", h);
  }

  /**
   * The #68721 situation: CPU actors serve external requests, IO actors receive external wakeups
   * (exporter-like), and k IO actors run a job that blocks for the whole measurement window.
   */
  static void ioblock(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    final List<Worker> cpuWorkers =
        submitWorkers(
            scheduler,
            "cpu",
            intArg("actors", 32),
            intArg("workNs", 5000),
            SchedulingHints.cpuBound());
    final List<Worker> ioWorkers =
        submitWorkers(
            scheduler,
            "io",
            intArg("ioActors", 8),
            intArg("ioWorkNs", 2000),
            SchedulingHints.ioBound());
    final int blockers = intArg("blockers", 1);
    final String kind = arg("blockKind", "native");
    final List<Plain> blockerActors = new ArrayList<>();
    for (int i = 0; i < blockers; i++) {
      final Plain b = new Plain("blocker" + i);
      scheduler.submitActor(b, SchedulingHints.ioBound()).join();
      blockerActors.add(b);
    }

    final AtomicLong cpuSent = new AtomicLong();
    final AtomicLong ioSent = new AtomicLong();
    final double rate = Double.parseDouble(arg("rate", "100000"));
    startClient("bench-client-0", rate / 2, cpuWorkers, cpuSent);
    startClient("bench-client-1", rate / 2, cpuWorkers, cpuSent);
    startClient("bench-client-io", Double.parseDouble(arg("ioRate", "20000")), ioWorkers, ioSent);

    Thread.sleep(warmupMs);
    // block for the whole window, plus a margin so the blocked state covers all of it
    final long blockMs = durationMs + 1000;
    final String mode = arg("blockMode", "auto");
    final boolean offload =
        mode.equals("offload") || (mode.equals("auto") && RUN_BLOCKING != null);
    OUT.put("block_offloaded", offload);
    for (final Plain b : blockerActors) {
      if (offload) {
        b.run(() -> b.offload(() -> block(kind, blockMs)));
      } else {
        b.run(() -> block(kind, blockMs));
      }
    }
    Thread.sleep(200);
    final CpuWindow cpu = new CpuWindow();
    final long ioSentAtStart = ioSent.get();
    measuring = true;
    Thread.sleep(durationMs);
    measuring = false;
    cpu.finish();
    final long ioSentInWindow = ioSent.get() - ioSentAtStart;
    running = false;
    final Histogram cpuH = merge(cpuWorkers);
    final Histogram ioH = merge(ioWorkers);
    OUT.put("throughput_per_s", Math.round(cpuH.getTotalCount() * 1000.0 / durationMs));
    putLatency("cpu", cpuH);
    putLatency("io", ioH);
    OUT.put("io_done_ratio", round2(ioH.getTotalCount() / (double) Math.max(1, ioSentInWindow)));
  }

  /** N actors with fixed-rate timers; measures timer jitter and idle-ish overhead. */
  static void timers(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    final int n = intArg("actors", 500);
    final long periodMs = intArg("periodMs", 10);
    final long workNs = intArg("workNs", 1000);
    final List<Histogram> hists = new ArrayList<>();
    final AtomicLong fired = new AtomicLong();
    for (int i = 0; i < n; i++) {
      final Histogram h = newHistogram();
      hists.add(h);
      final long[] last = {0};
      final Actor a =
          new Actor() {
            @Override
            protected void onActorStarted() {
              actor.runAtFixedRate(
                  Duration.ofMillis(periodMs),
                  () -> {
                    noteThread();
                    final long now = System.nanoTime();
                    if (measuring && last[0] != 0) {
                      h.recordValue(Math.abs(now - last[0] - periodMs * 1_000_000));
                      fired.incrementAndGet();
                    }
                    last[0] = now;
                    busy(workNs);
                  });
            }
          };
      scheduler.submitActor(a, SchedulingHints.cpuBound()).join();
    }
    phase = "warmup";
    Thread.sleep(warmupMs);
    phase = "measure";
    final CpuWindow cpu = new CpuWindow();
    measuring = true;
    Thread.sleep(durationMs);
    measuring = false;
    cpu.finish();
    running = false;
    final Histogram all = newHistogram();
    hists.forEach(all::add);
    OUT.put("throughput_per_s", Math.round(fired.get() * 1000.0 / durationMs));
    putLatency("jitter", all);
  }

  public static void main(final String[] args) throws Exception {
    for (final String a : args) {
      final int i = a.indexOf('=');
      ARGS.put(a.substring(0, i), a.substring(i + 1));
    }
    final String scenario = arg("scenario", "ingress");
    final int cpuThreads = intArg("cpu", 4);
    final int ioThreads = intArg("io", 4);
    final long warmupMs = intArg("warmupMs", 5000);
    final long durationMs = intArg("durationMs", 10000);

    OUT.put("variant", System.getProperty("bench.variant", "?"));
    OUT.put("rep", Integer.parseInt(System.getProperty("bench.rep", "0")));
    OUT.put("config", arg("config", scenario));
    OUT.put("scenario", scenario);
    ARGS.forEach((k, v) -> {
      if (!k.equals("config")) {
        OUT.put("arg_" + k, v);
      }
    });
    OUT.put("available_processors", Runtime.getRuntime().availableProcessors());

    final long watchdogMs = warmupMs + durationMs + intArg("graceMs", 30000);
    Thread.ofPlatform()
        .daemon()
        .name("bench-watchdog")
        .start(
            () -> {
              try {
                Thread.sleep(watchdogMs);
              } catch (final InterruptedException e) {
                return;
              }
              OUT.put("error", "watchdog: no result after " + watchdogMs + " ms, phase=" + phase);
              System.out.println("RESULT " + json());
              System.out.flush();
              Runtime.getRuntime().halt(2);
            });

    final ActorScheduler scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(cpuThreads)
            .setIoBoundActorThreadCount(ioThreads)
            .setSchedulerName("bench")
            .build();
    scheduler.start();

    switch (scenario) {
      case "pingpong" -> pingpong(scheduler, warmupMs, durationMs);
      case "fanout" -> fanout(scheduler, warmupMs, durationMs);
      case "ingress" -> ingress(scheduler, warmupMs, durationMs);
      case "ioblock" -> ioblock(scheduler, warmupMs, durationMs);
      case "timers" -> timers(scheduler, warmupMs, durationMs);
      case "fairness" -> fairness(scheduler, warmupMs, durationMs);
      case "broker" -> BrokerSim.run(scheduler, warmupMs, durationMs);
      default -> throw new IllegalArgumentException(scenario);
    }
    OUT.put("actor_on_virtual_thread", actorOnVirtualThread);
    System.out.println("RESULT " + json());
    System.out.flush();
    Runtime.getRuntime().halt(0);
  }
}
