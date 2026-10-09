/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Drives a synthetic workload through the broker of an AOT training run, so that the cache carries
 * method profiles for command processing and exporting, and not only for start-up.
 *
 * <p>Half of the load goes through gRPC, with a streaming job worker, and half through REST, with a
 * polling one. Each instance runs a service task, an exclusive gateway and, for every other
 * instance, a message catch event. Across several partitions, this also covers deployment
 * distribution and message correlation between partitions. The Elasticsearch exporter sends to a
 * {@link FakeElasticsearch}. Afterwards, the recording broker hands its partitions to the other
 * brokers of the cluster and takes them back several times, see {@link #changeLeaders}.
 */
public final class AotTrainingWorkload {

  private static final String PROCESS_ID = "aot-training";
  private static final String JOB_TYPE = "aot-training";
  private static final String MESSAGE_NAME = "aot-training";
  private static final int MAX_IN_FLIGHT = 32;
  private static final int RECORDER = 0;
  private static final int PEER = 1;
  private static final Duration LEADER_CHANGE_TIMEOUT = Duration.ofMinutes(1);
  // the cluster-admin user train.sh configures
  private static final String CLUSTER_ADMIN =
      "Basic " + Base64.getEncoder().encodeToString("aot-training:aot-training".getBytes(UTF_8));

  public static void main(final String[] args) throws Exception {
    final int instances = Integer.parseInt(args[0]);
    final int partitions = Integer.parseInt(args[1]);
    final int leaderChanges = Integer.parseInt(args[2]);
    final long recorderPid = Long.parseLong(args[3]);

    final var elasticsearch = new FakeElasticsearch();
    try (final var grpc = client(false, RECORDER);
        final var rest = client(true, RECORDER)) {
      awaitLeaders(grpc, partitions);
      grpc.newDeployResourceCommand()
          .addProcessModel(process(), PROCESS_ID + ".bpmn")
          .send()
          .join();

      try (final var grpcWorker = worker(grpc, true);
          final var restWorker = worker(rest, false)) {
        // The deployment reaches the other partitions asynchronously, so an early instance can
        // be rejected by a partition that does not know the process yet.
        for (int i = 0; i < partitions * 2; i++) {
          createWithRetry(grpc, "warmup-" + i);
        }

        final var inFlight = new Semaphore(MAX_IN_FLIGHT);
        final var futures = new CompletableFuture<?>[instances];
        final long start = System.nanoTime();
        for (int i = 0; i < instances; i++) {
          inFlight.acquire();
          futures[i] =
              run(i % 2 == 0 ? grpc : rest, "instance-" + i, i % 4 < 2)
                  .whenComplete((ignored, error) -> inFlight.release());
        }
        CompletableFuture.allOf(futures).get(10, TimeUnit.MINUTES);
        System.out.printf(
            "AOT training: completed %d process instances in %ds%n",
            instances, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
      }
      sendRequestShapes();
      changeLeaders(leaderChanges, recorderPid, partitions);
      elasticsearch.awaitExportDrained();
    } finally {
      elasticsearch.stop();
    }
  }

  private static CompletableFuture<?> run(
      final CamundaClient client, final String key, final boolean withMessage) {
    if (withMessage) {
      client
          .newPublishMessageCommand()
          .messageName(MESSAGE_NAME)
          .correlationKey(key)
          .timeToLive(Duration.ofMinutes(5))
          .send()
          .join();
    }
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(Map.of("key", key, "withMessage", withMessage))
        .withResult()
        .send()
        .toCompletableFuture();
  }

  /**
   * Sends JSON requests framed in each way a client may frame them, because the client above only
   * ever sends a body with a Content-Length. Spring's request mapping checks the framing of every
   * request, and code compiled from a profile that never saw the other framings is thrown away the
   * first time a production client uses one of them.
   */
  private static void sendRequestShapes() throws IOException {
    final String request =
        "POST /v2/process-instances/search HTTP/1.1\r\n"
            + "Host: localhost\r\nContent-Type: application/json\r\nConnection: close\r\n";
    final String[] shapes = {
      request + "Transfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n",
      request + "Content-Length: 0\r\n\r\n",
      request + "\r\n",
    };
    for (int i = 0; i < 100; i++) {
      for (final String shape : shapes) {
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), 8080)) {
          socket.getOutputStream().write(shape.getBytes(UTF_8));
          final var response =
              new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
          final String status = response.readLine();
          if (status == null || !status.startsWith("HTTP/1.1 200")) {
            throw new IllegalStateException(
                "Unexpected response to a framed search request: " + status);
          }
          response.transferTo(Writer.nullWriter());
        }
      }
    }
    System.out.printf("AOT training: sent %d framed requests%n", 100 * shapes.length);
  }

  /**
   * Hands the recording broker's partitions to the other brokers and back, so that the cache
   * carries a follower taking over leadership while commands keep arriving, which is what a
   * rebalance does in production. Each round pauses the recording broker until other brokers are
   * elected in its place, resumes it as a follower, and asks for a rebalance, which gives its
   * partitions back. Commands go through another broker's gateway, as the recording broker's
   * stops while it is paused, and their failures during the hand-over are expected.
   */
  private static void changeLeaders(final int rounds, final long recorderPid, final int partitions)
      throws Exception {
    final var running = new AtomicBoolean(true);
    final var inFlight = new Semaphore(8);
    try (final var peer = client(false, PEER);
        final var ignored = worker(peer, true)) {
      final var load =
          Thread.ofPlatform()
              .start(
                  () -> {
                    for (long i = 0; running.get(); i++) {
                      inFlight.acquireUninterruptibly();
                      peer.newCreateInstanceCommand()
                          .bpmnProcessId(PROCESS_ID)
                          .latestVersion()
                          .variables(Map.of("key", "leader-change-" + i, "withMessage", false))
                          .withResult()
                          .requestTimeout(Duration.ofSeconds(10))
                          .send()
                          .toCompletableFuture()
                          .whenComplete((result, error) -> inFlight.release());
                    }
                  });
      try {
        for (int round = 1; round <= rounds; round++) {
          signal(recorderPid, "STOP");
          try {
            await(
                "other brokers to lead every partition",
                () -> ledBy(peer, RECORDER).isEmpty() && ledByAny(peer) == partitions);
          } finally {
            signal(recorderPid, "CONT");
          }
          await("the recording broker to follow every partition", () -> follows(peer, partitions));
          await(
              "the recording broker to lead a partition again",
              () -> {
                rebalance();
                return !ledBy(peer, RECORDER).isEmpty();
              });
          // the commands that reach the new leader right after it took over are the point
          Thread.sleep(5_000);
        }
      } finally {
        running.set(false);
        load.join();
      }
    }
    System.out.printf("AOT training: moved leadership to the recording broker %d times%n", rounds);
  }

  private static List<Integer> ledBy(final CamundaClient client, final int nodeId) {
    final String memberId = String.valueOf(nodeId);
    final var brokers = topology(client);
    return brokers.stream()
        .filter(broker -> broker.getMemberId().equals(memberId))
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(partition -> partition.isLeader())
        .map(partition -> partition.getPartitionId())
        .filter(
            partitionId ->
                brokers.stream()
                    .filter(other -> !other.getMemberId().equals(memberId))
                    .flatMap(other -> other.getPartitions().stream())
                    .noneMatch(p -> p.getPartitionId() == partitionId && p.isLeader()))
        .toList();
  }

  private static long ledByAny(final CamundaClient client) {
    return topology(client).stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(partition -> partition.isLeader())
        .map(partition -> partition.getPartitionId())
        .distinct()
        .count();
  }

  private static boolean follows(final CamundaClient client, final int partitions) {
    return topology(client).stream()
        .filter(broker -> broker.getMemberId().equals(String.valueOf(RECORDER)))
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(partition -> partition.getRole() == PartitionBrokerRole.FOLLOWER)
        .filter(partition -> partition.getHealth() == PartitionBrokerHealth.HEALTHY)
        .count()
        == partitions;
  }

  private static List<BrokerInfo> topology(final CamundaClient client) {
    return client.newTopologyRequest().send().join().getBrokers();
  }

  private static void rebalance() throws IOException, InterruptedException {
    try (final var http = HttpClient.newHttpClient()) {
      final var response =
          http.send(
              HttpRequest.newBuilder(
                      URI.create(
                          "http://localhost:%d/cluster/v2/rebalance".formatted(8080 + PEER)))
                  .header("Accept", "application/json")
                  .header("Authorization", CLUSTER_ADMIN)
                  .POST(BodyPublishers.noBody())
                  .build(),
              BodyHandlers.ofString());
      if (response.statusCode() != 202) {
        throw new IllegalStateException(
            "Rebalance answered %d: %s".formatted(response.statusCode(), response.body()));
      }
    }
  }

  private static void signal(final long pid, final String signal)
      throws IOException, InterruptedException {
    final int exit =
        new ProcessBuilder("kill", "-" + signal, Long.toString(pid)).inheritIO().start().waitFor();
    if (exit != 0) {
      throw new IllegalStateException("kill -%s %d exited with %d".formatted(signal, pid, exit));
    }
  }

  private static void await(final String what, final Callable<Boolean> condition)
      throws Exception {
    final long deadline = System.nanoTime() + LEADER_CHANGE_TIMEOUT.toNanos();
    RuntimeException lastFailure = null;
    while (true) {
      try {
        if (condition.call()) {
          return;
        }
      } catch (final RuntimeException e) {
        // the topology or the rebalance can fail while leadership moves; ask again
        lastFailure = e;
      }
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("Timed out waiting for " + what, lastFailure);
      }
      Thread.sleep(1_000);
    }
  }

  private static void createWithRetry(final CamundaClient client, final String key)
      throws InterruptedException {
    for (int attempt = 1; ; attempt++) {
      try {
        run(client, key, false).join();
        return;
      } catch (final RuntimeException e) {
        if (attempt == 30) {
          throw e;
        }
        Thread.sleep(1_000);
      }
    }
  }

  private static void awaitLeaders(final CamundaClient client, final int partitions)
      throws InterruptedException {
    final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    while (true) {
      try {
        if (ledByAny(client) == partitions) {
          return;
        }
      } catch (final RuntimeException e) {
        if (System.nanoTime() > deadline) {
          throw e;
        }
      }
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("Timed out waiting for all partitions to have a leader");
      }
      Thread.sleep(1_000);
    }
  }

  /** A client of broker {@code nodeId}'s gateway, at the ports train.sh gives that broker. */
  private static CamundaClient client(final boolean preferRest, final int nodeId) {
    return CamundaClient.newClientBuilder()
        .restAddress(URI.create("http://localhost:" + (8080 + nodeId)))
        .grpcAddress(URI.create("http://localhost:" + (26500 + 10 * nodeId)))
        .preferRestOverGrpc(preferRest)
        .build();
  }

  private static JobWorker worker(final CamundaClient client, final boolean streamEnabled) {
    return client
        .newWorker()
        .jobType(JOB_TYPE)
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variables(Map.of("result", job.getKey() % 100))
                    .send()
                    .join())
        .streamEnabled(streamEnabled)
        .pollInterval(Duration.ofMillis(100))
        .open();
  }

  private static BpmnModelInstance process() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            "task",
            task ->
                task.zeebeJobType(JOB_TYPE)
                    .zeebeInputExpression("key", "input")
                    .zeebeOutputExpression("result + 1", "output"))
        .exclusiveGateway()
        .defaultFlow()
        .endEvent("end")
        .moveToLastExclusiveGateway()
        .conditionExpression("withMessage")
        .intermediateCatchEvent(
            "message",
            event -> event.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key")))
        .endEvent("messageEnd")
        .done();
  }

  /**
   * Answers the exporter on the default Elasticsearch address the way a cluster holding a little
   * history would, so that the exporter runs its real path -- handlers, batching, serialization,
   * the client and the parsing of each kind of response its background tasks read -- instead of
   * failing every request but the bulk ones. Whatever it does not know gets a 404, which the
   * exporter retries.
   */
  private static final class FakeElasticsearch {

    private static final byte[] NOT_FOUND =
        ("{\"error\":{\"type\":\"index_not_found_exception\",\"reason\":\"AOT training\"},"
                + "\"status\":404}")
            .getBytes(UTF_8);
    private static final String SHARDS =
        "\"_shards\":{\"total\":2,\"successful\":1,\"skipped\":0,\"failed\":0}";
    private static final Pattern FIELD = Pattern.compile("\"field\"\\s*:\\s*\"([^\"]+)\"");
    private static final int SEARCHES_WITH_A_HIT = 3;

    private final AtomicLong documents = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final Map<String, AtomicLong> searches = new ConcurrentHashMap<>();
    private final HttpServer server;

    private FakeElasticsearch() throws IOException {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200), 0);
      server.setExecutor(Executors.newFixedThreadPool(4));
      server.createContext("/", this::handle);
      server.start();
    }

    private void handle(final HttpExchange exchange) throws IOException {
      try (exchange) {
        final String request = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
        final String path = exchange.getRequestURI().getPath();
        final String query = Objects.requireNonNullElse(exchange.getRequestURI().getQuery(), "");
        int status = 200;
        final byte[] body;
        if (path.endsWith("/_bulk")) {
          body = bulkResponse(request);
        } else if (path.endsWith("/_search")) {
          body = searchResponse(path, query, request);
        } else if (path.endsWith("/_count")) {
          body = ("{\"count\":0," + SHARDS + "}").getBytes(UTF_8);
        } else if (path.endsWith("/_refresh")) {
          body = ("{" + SHARDS + "}").getBytes(UTF_8);
        } else if (exchange.getRequestMethod().equals("GET") && path.contains("/_doc/")) {
          body = documentResponse(path).getBytes(UTF_8);
        } else {
          status = 404;
          body = NOT_FOUND;
        }
        // The Elasticsearch client refuses any response without this header.
        exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
      }
    }

    /** Answers every action of the bulk request with success. */
    private byte[] bulkResponse(final String request) {
      final var items = new StringJoiner(",", "{\"took\":1,\"errors\":false,\"items\":[", "]}");
      final var lines = request.lines().filter(line -> !line.isBlank()).iterator();
      while (lines.hasNext()) {
        final String action = lines.next();
        final String type = action.substring(2, action.indexOf('"', 2));
        if (!type.equals("delete") && lines.hasNext()) {
          lines.next();
        }
        final long id = documents.incrementAndGet();
        final boolean created = type.equals("index") || type.equals("create");
        items.add(
            ("{\"%s\":{\"_index\":\"aot-training\",\"_id\":\"%d\",\"_version\":1,"
                    + "\"result\":\"%s\",%s,\"_seq_no\":%d,\"_primary_term\":1,\"status\":%d}}")
                .formatted(
                    type,
                    id,
                    created ? "created" : type.equals("update") ? "updated" : "deleted",
                    SHARDS,
                    id,
                    created ? 201 : 200));
      }
      return items.toString().getBytes(UTF_8);
    }

    /**
     * The first searches of an index find one document carrying every field the request asks for,
     * so that the tasks read a hit; later ones find nothing, so that they do not loop on it.
     */
    private byte[] searchResponse(final String path, final String query, final String request) {
      final boolean found =
          searches.computeIfAbsent(path, key -> new AtomicLong()).incrementAndGet()
              <= SEARCHES_WITH_A_HIT;
      final var fields = new StringJoiner(",", "{", "}");
      FIELD
          .matcher(request)
          .results()
          .map(match -> match.group(1))
          .distinct()
          .forEach(
              field -> fields.add("\"%s\":[\"2026-01-01T00:00:00.000+0000\"]".formatted(field)));
      final String hit =
          ("{\"_index\":\"aot-training\",\"_id\":\"%d\",\"_score\":null,\"_source\":{},"
                  + "\"fields\":%s,\"sort\":[1767225600000]}")
              .formatted(hits.incrementAndGet(), fields);
      return ("{\"took\":1,\"timed_out\":false,%s%s,\"hits\":{\"total\":"
              + "{\"value\":%d,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[%s]}}")
          .formatted(
              query.contains("scroll=") ? "\"_scroll_id\":\"aot-training\"," : "",
              SHARDS,
              found ? 1 : 0,
              found ? hit : "")
          .getBytes(UTF_8);
    }

    private String documentResponse(final String path) {
      final String id = path.substring(path.lastIndexOf('/') + 1);
      return ("{\"_index\":\"aot-training\",\"_id\":\"%s\",\"_version\":1,\"_seq_no\":0,"
              + "\"_primary_term\":1,\"found\":true,\"_source\":{\"key\":%s,"
              + "\"bpmnProcessId\":\"aot-training\",\"name\":\"aot-training\",\"version\":1}}")
          .formatted(id, id);
    }

    /** Waits until the exporter has caught up, and fails if it never exported anything. */
    private void awaitExportDrained() throws InterruptedException {
      final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
      long previous = -1;
      while (documents.get() != previous && System.nanoTime() < deadline) {
        previous = documents.get();
        Thread.sleep(3_000);
      }
      if (documents.get() == 0) {
        throw new IllegalStateException("The exporter never sent a bulk request");
      }
      System.out.printf("AOT training: exported %d documents%n", documents.get());
    }

    private void stop() {
      server.stop(0);
      ((ExecutorService) server.getExecutor()).shutdownNow();
    }
  }
}
