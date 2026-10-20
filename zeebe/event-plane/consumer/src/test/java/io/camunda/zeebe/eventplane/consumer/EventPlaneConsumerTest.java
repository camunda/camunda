/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import io.camunda.zeebe.eventplane.history.HistoryIds;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workers of a group, with short timings, on a store that tests write segments to. */
final class EventPlaneConsumerTest {

  private static final String GROUP = "g";
  private static final List<LogId> LOGS =
      List.of("default", "other").stream()
          .flatMap(tenant -> List.of(1, 2, 3).stream().map(p -> new LogId("c", tenant, p)))
          .toList();
  private static final Duration LEASE = Duration.ofSeconds(1);

  @TempDir Path directory;
  private ObjectStore store;
  private final Map<LogId, TestJournal> journals = new HashMap<>();
  private final Deliveries deliveries = new Deliveries();
  private final List<EventPlaneConsumer> workers = new ArrayList<>();
  private final List<AutoCloseable> resources = new ArrayList<>();

  @BeforeEach
  void setUp() throws IOException {
    store = new FileSystemStore(directory.resolve("store"));
    for (final var log : LOGS) {
      journals.put(log, new TestJournal(directory.resolve(log.tenant() + "-" + log.partition())));
      testLog(log).history(history(log), Base.NOTHING, Instant.parse("2026-10-20T12:00:00Z"));
      journals.get(log).initial();
      upload(log, 3);
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    workers.forEach(EventPlaneConsumer::close);
    for (final var resource : resources) {
      resource.close();
    }
    journals.values().forEach(TestJournal::close);
  }

  @Test
  void shouldDivideTheLogsAmongThreeWorkersAndDeliverEveryRecord() throws IOException {
    // given
    final var ids = List.of("w-x", "w-y", "w-z");
    final var expected = owners(ids);
    assertThat(expected.values()).allSatisfy(logs -> assertThat(logs).isNotEmpty());

    // when
    ids.forEach(this::worker);

    // then
    await()
        .untilAsserted(
            () -> {
              assertEveryRecordDelivered();
              for (final var worker : workers) {
                assertThat(worker.heldLogs()).isEqualTo(expected.get(worker.workerId()));
              }
            });
    for (final var log : LOGS) {
      upload(log, 2);
    }
    await().untilAsserted(this::assertEveryRecordDelivered);
    assertFirstArrivalsInPositionOrder();
  }

  @Test
  void shouldTakeOverAKilledWorkersLogsFromItsCursorAfterALease() throws IOException {
    // given
    final var expected = owners(List.of("w-x", "w-y"));
    final var killed = worker("w-x");
    final var survivor = worker("w-y");
    await()
        .untilAsserted(
            () -> {
              assertEveryRecordDelivered();
              assertThat(killed.heldLogs()).isEqualTo(expected.get("w-x"));
              assertCursorsAtTheEnd(LOGS);
              // w-y may have held them first; w-x names itself once it wrote its first cursors
              for (final var log : expected.get("w-x")) {
                assertThat(cursor(log).worker()).isEqualTo("w-x");
              }
            });
    deliveries.clear();

    // when
    killed.halt();
    assertThat(survivor.heldLogs()).isEqualTo(expected.get("w-y"));
    final var cursors = new HashMap<LogId, GroupCursor>();
    for (final var log : expected.get("w-x")) {
      cursors.put(log, cursor(log));
    }
    for (final var log : LOGS) {
      upload(log, 1);
    }

    // then
    await()
        .untilAsserted(
            () -> {
              assertThat(survivor.heldLogs()).containsExactlyInAnyOrderElementsOf(LOGS);
              assertEveryNewRecordDelivered();
            });
    for (final var log : expected.get("w-x")) {
      assertThat(cursors.get(log).worker()).isEqualTo("w-x");
      assertThat(deliveries.of("w-y", log))
          .containsExactlyElementsOf(
              range(cursors.get(log).cursor().position() + 1, journals.get(log).lastPosition()));
    }
    assertFirstArrivalsInPositionOrder();
  }

  @Test
  void shouldGiveAJoiningWorkerItsLogsAfterTheirHolderGaveThemUp() throws IOException {
    // given
    final var first = worker("w-x");
    await()
        .untilAsserted(
            () -> {
              assertEveryRecordDelivered();
              assertThat(first.heldLogs()).containsExactlyInAnyOrderElementsOf(LOGS);
              assertCursorsAtTheEnd(LOGS);
            });
    final long delivered = journals.get(LOGS.getFirst()).lastPosition();
    final var expected = owners(List.of("w-x", "w-y"));

    // when
    final var joining = worker("w-y");

    // then
    await()
        .untilAsserted(
            () -> {
              assertThat(first.heldLogs()).isEqualTo(expected.get("w-x"));
              assertThat(joining.heldLogs()).isEqualTo(expected.get("w-y"));
            });
    for (final var log : LOGS) {
      upload(log, 2);
    }
    await().untilAsserted(this::assertEveryRecordDelivered);
    for (final var log : expected.get("w-y")) {
      assertThat(deliveries.of("w-y", log)).allSatisfy(p -> assertThat(p).isGreaterThan(delivered));
    }
    assertFirstArrivalsInPositionOrder();
  }

  @Test
  void shouldMoveAClosedWorkersLogsAtOnce() throws IOException {
    // given a long lease, and the second worker's heartbeat there before the first starts, so
    // that the first never takes the second's logs
    final var lease = Duration.ofSeconds(30);
    store.put(ObjectNames.worker(GROUP, "w-y"), new Heartbeat("w-y", 0).toJson());
    final var expected = owners(List.of("w-x", "w-y"));
    final var closing = worker("w-x", builder -> builder.lease(lease));
    final var staying = worker("w-y", builder -> builder.lease(lease));
    await()
        .untilAsserted(
            () -> {
              assertEveryRecordDelivered();
              assertThat(closing.heldLogs()).isEqualTo(expected.get("w-x"));
              assertThat(staying.heldLogs()).isEqualTo(expected.get("w-y"));
            });
    deliveries.clear();

    // when
    closing.close();
    for (final var log : LOGS) {
      upload(log, 1);
    }

    // then well within the lease
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(staying.heldLogs()).containsExactlyInAnyOrderElementsOf(LOGS);
              assertEveryNewRecordDelivered();
            });
    assertThat(store.get(ObjectNames.worker(GROUP, "w-x"))).isEmpty();
    assertThat(deliveries.workers()).containsExactly("w-y");
  }

  @Test
  void shouldReadAtOnceWhenTheServiceNotifiesOfAnUpload() throws IOException {
    // given a service that passes on what the test sends, and a listing interval far away
    final var service = new SseStub();
    final var log = LOGS.getFirst();
    worker(
        "w-x", builder -> builder.service(service.uri()).listingInterval(Duration.ofSeconds(60)));
    await()
        .untilAsserted(
            () -> {
              assertEveryRecordDelivered();
              assertThat(service.queries())
                  .anyMatch(query -> query.contains("log=c%2Fdefault%2F1"));
            });

    // when
    service.send(notice(log, upload(log, 1)));

    // then
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(deliveries.distinct(log))
                    .containsExactlyElementsOf(range(1, journals.get(log).lastPosition())));
  }

  @Test
  void shouldListenAgainAfterTheStreamOfNotificationsDropped() throws IOException {
    // given
    final var service = new SseStub();
    final var log = LOGS.getFirst();
    worker(
        "w-x", builder -> builder.service(service.uri()).listingInterval(Duration.ofSeconds(60)));
    await()
        .untilAsserted(
            () -> {
              assertEveryRecordDelivered();
              assertThat(service.queries()).hasSize(1);
            });

    // when
    service.dropStreams();
    await().untilAsserted(() -> assertThat(service.queries()).hasSize(2));
    service.send(notice(log, upload(log, 1)));

    // then
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(deliveries.distinct(log))
                    .containsExactlyElementsOf(range(1, journals.get(log).lastPosition())));
  }

  private UploadNotice notice(final LogId log, final String key) {
    final var segment = ObjectNames.parseSegment(key);
    return new UploadNotice(
        log.cluster(),
        log.tenant(),
        log.partition(),
        segment.history(),
        key,
        segment.firstIndex(),
        segment.lastIndex(),
        1,
        journals.get(log).lastPosition(),
        1);
  }

  private EventPlaneConsumer worker(final String id) {
    return worker(id, UnaryOperator.identity());
  }

  private EventPlaneConsumer worker(
      final String id, final UnaryOperator<EventPlaneConsumer.Builder> configure) {
    final var builder =
        EventPlaneConsumer.builder()
            .store(store)
            .group(GROUP)
            .workerId(id)
            .subscribe("c", "*")
            .start(Start.EARLIEST)
            .handler((log, record) -> deliveries.add(id, log, record.getPosition()))
            .heartbeatInterval(Duration.ofMillis(50))
            .lease(LEASE)
            .cursorInterval(Duration.ofMillis(100))
            .listingInterval(Duration.ofMillis(100));
    final var worker = configure.apply(builder).build();
    workers.add(worker);
    worker.start();
    return worker;
  }

  /** Uploads segments of 4 entries of 3 job records each, and returns the last one's key. */
  private String upload(final LogId log, final int segments) throws IOException {
    String key = null;
    for (int i = 0; i < segments; i++) {
      key = testLog(log).upload(history(log), journals.get(log).jobEntries(4, 3));
    }
    return key;
  }

  private TestLog testLog(final LogId log) {
    return new TestLog(store, log);
  }

  private static UUID history(final LogId log) {
    return HistoryIds.initial(log.cluster(), log.tenant());
  }

  private GroupCursor cursor(final LogId log) throws IOException {
    return GroupCursor.fromJson(
        store
            .get(ObjectNames.cursor(GROUP, log.cluster(), log.tenant(), log.partition()))
            .orElseThrow());
  }

  /** Which logs each worker owns, among these workers. */
  private static Map<String, Set<LogId>> owners(final List<String> ids) {
    final var owners = new HashMap<String, Set<LogId>>();
    ids.forEach(id -> owners.put(id, new HashSet<>()));
    LOGS.forEach(log -> owners.get(Rendezvous.owner(ids, log)).add(log));
    return owners;
  }

  private void assertEveryRecordDelivered() {
    for (final var log : LOGS) {
      assertThat(deliveries.distinct(log))
          .describedAs("the records of log %s", log)
          .containsExactlyElementsOf(range(1, journals.get(log).lastPosition()));
    }
  }

  /** Every record uploaded after the deliveries were cleared, which were 36 per log. */
  private void assertEveryNewRecordDelivered() {
    for (final var log : LOGS) {
      assertThat(deliveries.distinct(log))
          .describedAs("the new records of log %s", log)
          .containsAll(range(37, journals.get(log).lastPosition()));
    }
  }

  private void assertCursorsAtTheEnd(final List<LogId> logs) throws IOException {
    for (final var log : logs) {
      assertThat(
              store
                  .get(ObjectNames.cursor(GROUP, log.cluster(), log.tenant(), log.partition()))
                  .map(json -> parse(json).cursor().position()))
          .describedAs("the cursor of log %s", log)
          .contains(journals.get(log).lastPosition());
    }
  }

  private static GroupCursor parse(final byte[] json) {
    try {
      return GroupCursor.fromJson(json);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void assertFirstArrivalsInPositionOrder() {
    for (final var log : LOGS) {
      assertThat(deliveries.firstArrivals(log)).describedAs("log %s", log).isSorted();
    }
  }

  private static List<Long> range(final long from, final long to) {
    return LongStream.rangeClosed(from, to).boxed().toList();
  }

  /** Every record that any worker delivered, in the order they arrived. */
  private static final class Deliveries {

    private final List<Delivery> all = new ArrayList<>();

    synchronized void add(final String worker, final LogId log, final long position) {
      all.add(new Delivery(worker, log, position));
    }

    synchronized void clear() {
      all.clear();
    }

    synchronized List<Long> distinct(final LogId log) {
      return all.stream()
          .filter(delivery -> delivery.log().equals(log))
          .map(Delivery::position)
          .distinct()
          .sorted()
          .toList();
    }

    synchronized List<Long> firstArrivals(final LogId log) {
      final var seen = new LinkedHashSet<Long>();
      all.stream()
          .filter(delivery -> delivery.log().equals(log))
          .forEach(delivery -> seen.add(delivery.position()));
      return List.copyOf(seen);
    }

    synchronized List<Long> of(final String worker, final LogId log) {
      return all.stream()
          .filter(delivery -> delivery.worker().equals(worker) && delivery.log().equals(log))
          .map(Delivery::position)
          .toList();
    }

    synchronized Set<String> workers() {
      return all.stream().map(Delivery::worker).collect(Collectors.toSet());
    }
  }

  private record Delivery(String worker, LogId log, long position) {}

  /** The service's stream of notifications, which sends what the test tells it to. */
  private final class SseStub {

    private final HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<OutputStream> streams = new CopyOnWriteArrayList<>();

    SseStub() throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      final var executor = Executors.newCachedThreadPool();
      server.setExecutor(executor);
      resources.add(executor::shutdownNow);
      server.createContext(
          "/v1/notifications",
          exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            final var body = exchange.getResponseBody();
            synchronized (this) {
              body.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
              body.flush();
            }
            streams.add(body);
          });
      server.start();
      resources.add(() -> server.stop(0));
    }

    URI uri() {
      return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    List<String> queries() {
      return List.copyOf(queries);
    }

    synchronized void dropStreams() throws IOException {
      for (final var stream : streams) {
        stream.close();
      }
      streams.clear();
    }

    synchronized void send(final UploadNotice notice) {
      final var event =
          "event: upload\ndata: " + new String(notice.toJson(), StandardCharsets.UTF_8) + "\n\n";
      for (final var stream : streams) {
        try {
          stream.write(event.getBytes(StandardCharsets.UTF_8));
          stream.flush();
        } catch (final IOException e) {
          streams.remove(stream);
        }
      }
    }
  }
}
