/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Compares the bytes that a process instance writes to the log with a baseline file. A change that
 * makes records much larger, or writes more of them, fails this test.
 *
 * <h2>Why the size of the records is important</h2>
 *
 * <p>Each byte that the engine writes to the log has a cost for the user. The broker replicates it,
 * writes it to disk, keeps it in snapshots and backups, and exports it. Larger records thus make
 * the disk and the backup storage larger, and they can decrease the throughput. A small growth per
 * record is large in total, because the engine writes many records for each process instance.
 *
 * <p>For example, the engine copied the full token claims of a client command to all follow-up
 * records since 8.9. With OIDC clients, this made the log about two times larger. No test showed
 * this growth, and it was found only after the release.
 *
 * <h2>When this test fails</h2>
 *
 * <p>Do not only update the baseline. First find the cause of the change, and decide if it is
 * necessary:
 *
 * <ul>
 *   <li>Is the new data necessary on each record, or only on some records?
 *   <li>Does a reader of the new data exist, for example an exporter or a processor?
 *   <li>Can a smaller encoding or a reference replace the data?
 * </ul>
 *
 * <p>If the growth is necessary, update the baseline, and explain the reason in the pull request.
 * The reviewer then sees the size change in the diff of the baseline file. A decrease also fails
 * this test, so that the baseline stays accurate. Update the baseline with:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine -Dtest=RecordSizeBaselineTest -Drecord-size.update=true \
 *   -DskipTests=false -DskipITs -Dquickly
 * </pre>
 */
public final class RecordSizeBaselineTest {

  private static final String UPDATE_PROPERTY = "record-size.update";
  private static final String BASELINE_FILE = "record-size-baseline.txt";

  /**
   * The allowed change per group, in percent. It is small enough to show a new field on many
   * records, and large enough to allow small changes of the encoding.
   */
  private static final int TOLERANCE_PERCENT = 10;

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotChangeRecordSizesOfServiceTaskProcess() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final long startPosition = lastPosition();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(oidcClaims());
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    final var actual = measure(startPosition);
    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      writeBaseline(actual);
      return;
    }
    final var baseline = readBaseline();
    assertThat(changes(baseline, actual))
        .as(
            "The records written to the log changed by more than %d%%. Larger records cost the"
                + " user disk, backup storage, and throughput. Read the Javadoc of %s before you"
                + " update the baseline",
            TOLERANCE_PERCENT, getClass().getSimpleName())
        .isEmpty();
  }

  /** Returns the groups that are new, removed, or out of the tolerance, with both sizes. */
  private static List<String> changes(
      final Map<String, Size> baseline, final Map<String, Size> actual) {
    final var groups = new TreeSet<String>(baseline.keySet());
    groups.addAll(actual.keySet());
    final var changes = new ArrayList<String>();
    for (final var group : groups) {
      final var expected = baseline.get(group);
      final var size = actual.get(group);
      if (expected == null
          || size == null
          || size.bytes() < lowerBound(expected.bytes())
          || size.bytes() > upperBound(expected.bytes())) {
        changes.add("%s: baseline %s, now %s".formatted(group, expected, size));
      }
    }
    return changes;
  }

  private long lastPosition() {
    return engine.events().map(LoggedEvent::getPosition).reduce((a, b) -> b).orElse(-1L);
  }

  /** Groups the log entries after the given position by record type and value type. */
  private Map<String, Size> measure(final long afterPosition) {
    final var sizes = new TreeMap<String, Size>();
    final var metadata = new RecordMetadata();
    engine
        .events()
        .filter(event -> event.getPosition() > afterPosition)
        .forEach(
            event -> {
              metadata.reset();
              event.readMetadata(metadata);
              final var group = metadata.getRecordType() + " " + metadata.getValueType();
              final int bytes = event.getMetadataLength() + event.getValueLength();
              sizes.merge(group, new Size(1, bytes), Size::plus);
            });
    return sizes;
  }

  private static long lowerBound(final long bytes) {
    return bytes * (100 - TOLERANCE_PERCENT) / 100;
  }

  private static long upperBound(final long bytes) {
    return bytes * (100 + TOLERANCE_PERCENT) / 100;
  }

  /**
   * Claims as an OIDC client sends them, with a realistic size. The size of the authorization data
   * on the follow-up records depends on them.
   */
  private static AuthInfo oidcClaims() {
    final var tokenClaims = new TreeMap<String, Object>();
    tokenClaims.put("iss", "https://idp.example.com/realms/camunda-platform");
    tokenClaims.put("sub", "8f14e45f-ceea-467f-a0e6-3c2d1b9f7a10");
    tokenClaims.put("aud", List.of("orchestration-api", "account"));
    tokenClaims.put("azp", "orchestration");
    tokenClaims.put("exp", 1_800_000_000L);
    tokenClaims.put("iat", 1_799_999_700L);
    tokenClaims.put("auth_time", 1_799_999_700L);
    tokenClaims.put("jti", "onrtac:5b8e6c1d-2f3a-4b7c-9d8e-1f2a3b4c5d6e");
    tokenClaims.put("typ", "Bearer");
    tokenClaims.put("sid", "c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f");
    tokenClaims.put("acr", "1");
    tokenClaims.put("scope", "openid profile email");
    tokenClaims.put("email_verified", true);
    tokenClaims.put("name", "Demo User");
    tokenClaims.put("preferred_username", "demo");
    tokenClaims.put("given_name", "Demo");
    tokenClaims.put("family_name", "User");
    tokenClaims.put("email", "demo@example.com");
    tokenClaims.put(
        "realm_access",
        Map.of("roles", List.of("default-roles-camunda-platform", "offline_access", "uma")));
    tokenClaims.put(
        "resource_access",
        Map.of("account", Map.of("roles", List.of("manage-account", "view-profile"))));
    tokenClaims.put("groups", groups());

    final var claims = new TreeMap<String, Object>();
    claims.put(Authorization.AUTHORIZED_USERNAME, "demo");
    claims.put(Authorization.USER_GROUPS_CLAIMS, groups());
    claims.put(Authorization.USER_TOKEN_CLAIMS, tokenClaims);
    return AuthInfo.ofClaims(claims);
  }

  private static List<String> groups() {
    return IntStream.rangeClosed(1, 10).mapToObj(i -> "/camunda/department-" + i).toList();
  }

  private static Map<String, Size> readBaseline() {
    try {
      final var sizes = new TreeMap<String, Size>();
      for (final var line : Files.readAllLines(baselinePath(), StandardCharsets.UTF_8)) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        final var fields = line.split(" ");
        sizes.put(
            fields[0] + " " + fields[1],
            new Size(Integer.parseInt(fields[2]), Long.parseLong(fields[3])));
      }
      return sizes;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read the baseline " + baselinePath(), e);
    }
  }

  private static void writeBaseline(final Map<String, Size> sizes) {
    final var lines = new ArrayList<String>();
    lines.add("# Written by RecordSizeBaselineTest. Do not edit by hand.");
    lines.add("# <record type> <value type> <record count> <bytes of metadata and value>");
    sizes.forEach((group, size) -> lines.add(group + " " + size.count() + " " + size.bytes()));
    try {
      Files.write(baselinePath(), lines, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write the baseline " + baselinePath(), e);
    }
  }

  /** Surefire runs the tests in the module directory. */
  private static Path baselinePath() {
    var dir = Path.of("src", "test", "resources");
    for (final var segment : RecordSizeBaselineTest.class.getPackageName().split("\\.")) {
      dir = dir.resolve(segment);
    }
    return dir.resolve(BASELINE_FILE);
  }

  private record Size(int count, long bytes) {
    Size plus(final Size other) {
      return new Size(count + other.count, bytes + other.bytes);
    }

    @Override
    public String toString() {
      return count + " records, " + bytes + " bytes";
    }
  }
}
