/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Drives a steady but <em>uneven</em>, varied live stream through the whole analytics stack: it
 * deploys several distinct process definitions with real service tasks + job workers (and one
 * laid-out, timer-based model), then continuously starts instances that actually <em>complete</em>
 * with varied durations. Feeds every dashboard metric at once — per-definition duration
 * percentiles, SLA-met and no-incident ratios, distinct-process count, the top-processes ranking,
 * and the flow-node duration heatmap.
 *
 * <p>Variation is deliberate so the charts have shape:
 *
 * <ul>
 *   <li>arrival is bursty and jittered, not a fixed cadence (occasional bursts, occasional pauses);
 *   <li>processes are picked by weight, so the top-processes ranking is uneven;
 *   <li>job workers wait a randomized time before completing, with a fat tail (~10% take much
 *       longer) so p90/p99 sit well above the median.
 * </ul>
 *
 * <p>Processes deployed:
 *
 * <ul>
 *   <li>{@code order-process} — start → service task ({@code order-collect}) → end.
 *   <li>{@code payment-process} — start → service task ({@code payment-authorize}) → exclusive
 *       gateway → (approved) service task ({@code payment-capture}) → end / (declined) → end.
 *   <li>{@code shipping-process} — start → parallel fork → dispatch ∥ notify-customer → join → end;
 *       the parallel branches exercise the variant signature's interleaving-insensitivity.
 *   <li>{@code claim-process} — the variant showcase: a triage gateway (auto / manual / fraud
 *       routes, weighted by start variables incl. a claim {@code amount}), a genuine retry loop on
 *       the manual assessment (feeds rework hotspots), and a fraud rejection end. See {@link
 *       #claimProcess()}.
 *   <li>{@code region-exec-time-demo} — a laid-out, timer-based model deployed from the bundled
 *       {@code region-exec-time-demo.bpmn}; completes on its own via region-dependent timers.
 * </ul>
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.MultiProcessDemoDriver [meanIntervalMs]
 *   -Dcamunda.rest=http://localhost:8088
 * </pre>
 *
 * Runs until interrupted (Ctrl-C / kill).
 */
public final class MultiProcessDemoDriver {

  private static final String[] REGIONS = {"EU", "US", "APAC"};
  private static final String REGION_DEMO = "region-exec-time-demo";

  // Instances run a few seconds (never longer), so completions keep pace with arrival and every
  // process's cube windows stay dense. ~15% do deliberately slow work that blows the ~9s SLA (and
  // lifts p90/p99); ~8%
  // raise a real Zeebe incident (job failed with no retries left). A faulted instance is cancelled
  // shortly after so it terminates and registers as "did not finish cleanly" in the no-incident
  // ratio — otherwise a real incident just hangs the instance and never completes.
  private static final int SLA_BREACH_PCT = 15;
  private static final int FAULT_PCT = 8;
  private static final int INCIDENT_LEFT_OPEN_PCT = 30; // of faults: left open instead of cancelled
  private static final long FAULT_CANCEL_DELAY_MS = 2_000L;
  // Ample execution threads so jobs run concurrently instead of serialising into a queue (the
  // client
  // defaults to ONE). Sized well above the expected in-flight job count (arrival rate × work time)
  // so the worker pool never saturates — a saturated pool stalls completions, which starves the
  // service-task processes' cube windows and gaps their timelines while the timer-based process
  // (no workers) stays dense.
  private static final int WORKER_THREADS = 256;
  private static final Duration JOB_TIMEOUT = Duration.ofMinutes(1); // must exceed the longest work

  private MultiProcessDemoDriver() {}

  public static void main(final String[] args) throws InterruptedException {
    final var restAddress = System.getProperty("camunda.rest", "http://localhost:8088");
    final long meanIntervalMs = args.length > 0 ? Long.parseLong(args[0]) : 600L;

    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(URI.create(restAddress))
            .preferRestOverGrpc(true)
            .numJobWorkerExecutionThreads(WORKER_THREADS)
            .build();

    deploy(client, "order-process", orderProcess());
    deploy(client, "payment-process", paymentProcess());
    deploy(client, "shipping-process", shippingProcess());
    deploy(client, "claim-process", claimProcess());
    // a laid-out, timer-based model deployed from its bundled resource (completes on its own)
    client.newDeployResourceCommand().addResourceFromClasspath(REGION_DEMO + ".bpmn").send().join();
    System.out.println("Deployed '" + REGION_DEMO + "'");

    // weighted mix so the top-processes ranking is uneven
    final List<String> weighted =
        List.of(
            "order-process",
            "order-process",
            "order-process",
            "order-process",
            "payment-process",
            "payment-process",
            "payment-process",
            "shipping-process",
            "shipping-process",
            "claim-process",
            "claim-process",
            "claim-process",
            REGION_DEMO,
            REGION_DEMO);

    // cancels faulted (incident-raising) instances a moment after the incident, off the worker
    // thread, so they terminate rather than hang forever.
    final ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();

    final List<JobWorker> workers = new ArrayList<>();
    workers.add(worker(client, canceller, "order-collect"));
    workers.add(worker(client, canceller, "shipping-dispatch"));
    workers.add(worker(client, canceller, "shipping-notify"));
    workers.add(worker(client, canceller, "payment-capture"));
    workers.add(worker(client, canceller, "claim-register"));
    workers.add(worker(client, canceller, "claim-assess-auto"));
    workers.add(worker(client, canceller, "claim-payout"));
    // the manual assessor drives the retry loop: resolved only once the planned passes are done
    workers.add(
        client
            .newWorker()
            .jobType("claim-assess-manual")
            .handler(
                (jobClient, job) -> {
                  sleepWork();
                  final var vars = job.getVariablesAsMap();
                  final int pass = ((Number) vars.getOrDefault("pass", 0)).intValue() + 1;
                  final int planned = ((Number) vars.getOrDefault("plannedPasses", 1)).intValue();
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables(Map.of("pass", pass, "resolved", pass >= planned))
                      .send()
                      .join();
                })
            .name("claim-assess-manual-worker")
            .maxJobsActive(WORKER_THREADS)
            .timeout(JOB_TIMEOUT)
            .open());
    // the fraud check rejects a share of its (already rare) route outright
    workers.add(
        client
            .newWorker()
            .jobType("claim-fraud-check")
            .handler(
                (jobClient, job) -> {
                  sleepWork();
                  final boolean fraudulent = ThreadLocalRandom.current().nextInt(100) < 40;
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables(Map.of("fraudulent", fraudulent))
                      .send()
                      .join();
                })
            .name("claim-fraud-check-worker")
            .maxJobsActive(WORKER_THREADS)
            .timeout(JOB_TIMEOUT)
            .open());
    // the authorize task decides the gateway branch by setting the `approved` variable
    workers.add(
        client
            .newWorker()
            .jobType("payment-authorize")
            .handler(
                (jobClient, job) -> {
                  sleepWork();
                  final boolean approved = ThreadLocalRandom.current().nextInt(100) < 80;
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables(Map.of("approved", approved))
                      .send()
                      .join();
                })
            .name("payment-authorize-worker")
            .maxJobsActive(WORKER_THREADS)
            .timeout(JOB_TIMEOUT)
            .open());

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  workers.forEach(JobWorker::close);
                  canceller.shutdownNow();
                  client.close();
                }));

    System.out.println(
        "Deployed processes; starting instances with bursty, jittered arrival (Ctrl-C to stop)…");
    long started = 0;
    while (!Thread.currentThread().isInterrupted()) {
      final var rnd = ThreadLocalRandom.current();
      // occasional burst; otherwise a single start followed by a jittered (sometimes long) gap
      final int burst = rnd.nextInt(100) < 15 ? rnd.nextInt(3, 7) : 1;
      for (int b = 0; b < burst; b++) {
        startInstance(client, weighted.get(rnd.nextInt(weighted.size())));
        if (++started % 25 == 0) {
          System.out.println("started " + started + " instances");
        }
        if (b < burst - 1) {
          Thread.sleep(rnd.nextLong(20, 80)); // tight gaps inside a burst
        }
      }
      // jittered gap between arrivals; ~1 in 12 is a longer lull
      final long gap =
          rnd.nextInt(12) == 0
              ? rnd.nextLong(meanIntervalMs * 3, meanIntervalMs * 6)
              : rnd.nextLong(meanIntervalMs / 3, meanIntervalMs * 2);
      Thread.sleep(gap);
    }
  }

  private static void startInstance(final CamundaClient client, final String process) {
    final var rnd = ThreadLocalRandom.current();
    final String region = REGIONS[rnd.nextInt(REGIONS.length)];
    final Map<String, Object> vars = new HashMap<>();
    vars.put("region", region);
    // Order and payment carry a business amount too, so the value KPIs (value processed /
    // value in flight) tell a story across the whole demo, not only for claims.
    if (process.equals("order-process")) {
      vars.put("amount", 20L + rnd.nextLong(480L));
    }
    if (process.equals("payment-process")) {
      vars.put("amount", 10L + rnd.nextLong(1_990L));
    }
    if (process.equals("claim-process")) {
      // Weighted triage route (the variant driver) and a claim amount: high claims skew toward
      // manual review, so the amount is a correlation hook, not just a value-KPI input.
      final long amount = 50L + rnd.nextLong(5_000L);
      final int roll = rnd.nextInt(100);
      final String route = roll < 5 ? "fraud" : roll < 30 || amount > 4_000L ? "manual" : "auto";
      vars.put("route", route);
      vars.put("amount", amount);
      // How many assessment passes a manual claim needs (the loop count): mostly one, some two,
      // few three — the source of the loop variants and the rework hotspot.
      vars.put("plannedPasses", rnd.nextInt(100) < 60 ? 1 : rnd.nextInt(100) < 70 ? 2 : 3);
      vars.put("pass", 0);
    }
    if (process.equals(REGION_DEMO)) {
      // region-dependent timer durations so per-element execution times differ on the heatmap
      final double processSecs = 0.5 + rnd.nextInt(REGIONS.length + region.length() % 3) * 0.5;
      final double reviewSecs = 0.3 + rnd.nextInt(3) * 0.2;
      vars.put("delay", "PT" + processSecs + "S");
      vars.put("reviewDelay", "PT" + reviewSecs + "S");
    }
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(process)
        .latestVersion()
        .variables(vars)
        .send()
        .join();
  }

  /**
   * Randomized work time on a few-seconds scale so instances complete fast enough that completions
   * keep pace with arrival (the worker pool never saturates) — this keeps every process's cube
   * windows dense and their timelines gap-free. The spread still gives the charts shape: {@link
   * #SLA_BREACH_PCT}% are deliberately slow (10-16s) so they blow the ~9s SLA and lift p90/p99; the
   * rest complete comfortably under it.
   */
  private static void sleepWork() throws InterruptedException {
    final var rnd = ThreadLocalRandom.current();
    final int r = rnd.nextInt(100);
    if (r < SLA_BREACH_PCT) {
      Thread.sleep(rnd.nextLong(10_000, 16_000)); // deliberately slow — blows the ~9s SLA
    } else if (r < SLA_BREACH_PCT + 20) {
      Thread.sleep(rnd.nextLong(4_500, 8_500)); // near the target, still meets it
    } else {
      Thread.sleep(rnd.nextLong(500, 4_500)); // typical, comfortably under
    }
  }

  private static void deploy(
      final CamundaClient client, final String processId, final BpmnModelInstance model) {
    client.newDeployResourceCommand().addProcessModel(model, processId + ".bpmn").send().join();
    System.out.println("Deployed '" + processId + "'");
  }

  private static BpmnModelInstance orderProcess() {
    return Bpmn.createExecutableProcess("order-process")
        .startEvent("order-received")
        .serviceTask("collect", t -> t.zeebeJobType("order-collect"))
        .endEvent()
        .done();
  }

  // dispatch and the customer notification run on PARALLEL branches: their completion facts
  // interleave arbitrarily, which is exactly what the variant signature's order-insensitivity
  // must absorb — every shipping instance forms ONE variant regardless of interleaving.
  private static BpmnModelInstance shippingProcess() {
    return Bpmn.createExecutableProcess("shipping-process")
        .startEvent("shipment-requested")
        .parallelGateway("fork")
        .serviceTask("dispatch", t -> t.zeebeJobType("shipping-dispatch"))
        .parallelGateway("join")
        .endEvent("shipped")
        .moveToNode("fork")
        .serviceTask("notify-customer", t -> t.zeebeJobType("shipping-notify"))
        .connectTo("join")
        .done();
  }

  // start -> authorize -> XOR gateway -> (approved) capture -> end / (not approved) declined end
  private static BpmnModelInstance paymentProcess() {
    return Bpmn.createExecutableProcess("payment-process")
        .startEvent("payment-requested")
        .serviceTask("authorize", t -> t.zeebeJobType("payment-authorize"))
        .exclusiveGateway("decision")
        .conditionExpression("=approved")
        .serviceTask("capture", t -> t.zeebeJobType("payment-capture"))
        .endEvent("captured")
        .moveToLastGateway()
        .conditionExpression("=not(approved)")
        .endEvent("declined")
        .done();
  }

  /**
   * The variant-analysis showcase: one process whose instances genuinely walk different paths.
   * Start variables pick a triage route (auto / manual / fraud-check); the manual route loops the
   * assessment until resolved (a real retry loop — it also feeds the rework hotspots, since {@code
   * assess-manual} activates more often than its instance count), and the fraud route can reject
   * outright. Every branch except a rejection converges on the payout.
   *
   * <pre>
   *   register → [triage] ─ auto ──→ assess-auto ────────────────→ [merge] → payout → paid
   *                       ├ manual → assess-manual → [resolved?] ─ yes ──↑
   *                       │              ↑____________ no ______________│
   *                       └ fraud ──→ fraud-check → [fraud?] ─ no ──────↑
   *                                                          └ yes → rejected
   * </pre>
   */
  private static BpmnModelInstance claimProcess() {
    return Bpmn.createExecutableProcess("claim-process")
        .startEvent("claim-received")
        .serviceTask("register", t -> t.zeebeJobType("claim-register"))
        .exclusiveGateway("triage")
        .conditionExpression("=route = \"auto\"")
        .serviceTask("assess-auto", t -> t.zeebeJobType("claim-assess-auto"))
        .exclusiveGateway("merge")
        .serviceTask("payout", t -> t.zeebeJobType("claim-payout"))
        .endEvent("paid")
        .moveToNode("triage")
        .conditionExpression("=route = \"manual\"")
        .serviceTask("assess-manual", t -> t.zeebeJobType("claim-assess-manual"))
        .exclusiveGateway("resolved-check")
        .conditionExpression("=resolved")
        .connectTo("merge")
        .moveToNode("resolved-check")
        .conditionExpression("=not(resolved)")
        .connectTo("assess-manual")
        .moveToNode("triage")
        .conditionExpression("=route = \"fraud\"")
        .serviceTask("fraud-check", t -> t.zeebeJobType("claim-fraud-check"))
        .exclusiveGateway("fraud-decision")
        .conditionExpression("=fraudulent")
        .endEvent("rejected")
        .moveToNode("fraud-decision")
        .conditionExpression("=not(fraudulent)")
        .connectTo("merge")
        .done();
  }

  private static JobWorker worker(
      final CamundaClient client, final ScheduledExecutorService canceller, final String jobType) {
    return client
        .newWorker()
        .jobType(jobType)
        .handler((jobClient, job) -> handleJob(client, canceller, jobClient, job))
        .name(jobType + "-worker")
        .maxJobsActive(WORKER_THREADS)
        .timeout(JOB_TIMEOUT)
        .open();
  }

  /**
   * Completes the job after some work, except for {@link #FAULT_PCT}% of jobs which fail with no
   * retries left — raising a real incident. Most faulted instances are then cancelled after a short
   * delay (resolving the incident, so it counts toward incident duration and the no-incident
   * ratio); a share ({@link #INCIDENT_LEFT_OPEN_PCT}%) are left alone, so their incident stays open
   * and the open-incidents gauge is non-zero.
   */
  private static void handleJob(
      final CamundaClient client,
      final ScheduledExecutorService canceller,
      final JobClient jobClient,
      final ActivatedJob job)
      throws InterruptedException {
    final var rnd = ThreadLocalRandom.current();
    if (rnd.nextInt(100) < FAULT_PCT) {
      jobClient
          .newFailCommand(job.getKey())
          .retries(0)
          .errorMessage("injected fault: downstream dependency unavailable")
          .send()
          .join();
      if (rnd.nextInt(100) < INCIDENT_LEFT_OPEN_PCT) {
        return; // leave the incident open — feeds the open-incidents gauge/heatmap
      }
      final long instanceKey = job.getProcessInstanceKey();
      canceller.schedule(
          () -> {
            try {
              client.newCancelInstanceCommand(instanceKey).send().join();
            } catch (final Exception ignored) {
              // instance may already be gone — best effort
            }
          },
          FAULT_CANCEL_DELAY_MS,
          TimeUnit.MILLISECONDS);
      return;
    }
    sleepWork();
    jobClient.newCompleteCommand(job.getKey()).send().join();
  }
}
