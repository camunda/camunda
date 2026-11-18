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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Compares the log bytes of typical scenarios with a baseline. A change of more than 10%, up or
 * down, fails.
 *
 * <p>Each byte on the log costs the user disk, backup storage, and throughput. For example, in 8.9
 * the engine copied all token claims to each follow-up record, and the log became two times larger.
 * Thus, find the cause of a failure before you update the baseline. Make sure that each record
 * needs the new data, and that a reader for it exists.
 *
 * <p>Update the baseline, and give the reason in the pull request:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine -Dtest=RecordSizeBaselineTest -Drecord-size.update=true \
 *   -DskipTests=false -DskipITs -Dquickly
 * </pre>
 */
public final class RecordSizeBaselineTest {

  private static final String UPDATE_PROPERTY = "record-size.update";
  private static final String BASELINE_FOLDER = "record-size-baseline";

  /** Large enough for small encoding changes, small enough for a new field on many records. */
  private static final int TOLERANCE_PERCENT = 10;

  /** Scheduled tasks write these records at times that the scenarios do not control. */
  private static final Set<ValueType> TIMED_VALUE_TYPES =
      Set.of(ValueType.JOB_METRICS_BATCH, ValueType.USAGE_METRIC);

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task";
  private static final AuthInfo CLAIMS = oidcClaims();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotChangeRecordSizesOfServiceTask() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done());
    final long startPosition = lastPosition();

    // when
    final long processInstanceKey = createProcessInstance();
    completeJob(jobKey(processInstanceKey, JOB_TYPE));
    awaitCompletion(processInstanceKey);

    // then
    assertBaseline("service-task", startPosition);
  }

  @Test
  public void shouldNotChangeRecordSizesOfUserTaskWithTaskListener() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeTaskListener(l -> l.completing().type("listener"))
            .endEvent()
            .done());
    final long startPosition = lastPosition();

    // when
    final long processInstanceKey = createProcessInstance();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.writeRecords(
        RecordToWrite.commandFromRequest(CLAIMS)
            .userTask(UserTaskIntent.COMPLETE, new UserTaskRecord().setUserTaskKey(userTaskKey))
            .key(userTaskKey));
    completeJob(jobKey(processInstanceKey, "listener"));
    awaitCompletion(processInstanceKey);

    // then
    assertBaseline("user-task-with-task-listener", startPosition);
  }

  @Test
  public void shouldNotChangeRecordSizesOfMessageCorrelation() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(
                "message", e -> e.message(m -> m.name("message").zeebeCorrelationKey("=key")))
            .endEvent()
            .done());
    final long startPosition = lastPosition();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-1")
            .create(CLAIMS);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    engine.writeRecords(
        RecordToWrite.commandFromRequest(CLAIMS)
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName("message")
                    .setCorrelationKey("order-1")
                    .setTimeToLive(0)));
    awaitCompletion(processInstanceKey);

    // then
    assertBaseline("message-correlation", startPosition);
  }

  @Test
  public void shouldNotChangeRecordSizesOfIncidentResolution() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done());
    final long startPosition = lastPosition();

    // when
    final long processInstanceKey = createProcessInstance();
    final long jobKey = jobKey(processInstanceKey, JOB_TYPE);
    writeJobCommand(jobKey, JobIntent.FAIL, new JobRecord().setRetries(0).setErrorMessage("fail"));
    final long incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    writeJobCommand(jobKey, JobIntent.UPDATE_RETRIES, new JobRecord().setRetries(1));
    engine.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve(CLAIMS);
    completeJob(jobKey);
    awaitCompletion(processInstanceKey);

    // then
    assertBaseline("incident-resolution", startPosition);
  }

  @Test
  public void shouldNotChangeRecordSizesOfTimer() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT1M"))
            .endEvent()
            .done());
    final long startPosition = lastPosition();

    // when
    final long processInstanceKey = createProcessInstance();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    engine.increaseTime(Duration.ofMinutes(1));
    awaitCompletion(processInstanceKey);

    // then
    assertBaseline("timer", startPosition);
  }

  @Test
  public void shouldNotChangeRecordSizesOfRejections() {
    // given
    final long startPosition = lastPosition();

    // when
    engine.processInstance().ofBpmnProcessId("unknown").expectRejection().create(CLAIMS);
    writeJobCommand(1L, JobIntent.COMPLETE, new JobRecord());
    RecordingExporter.jobRecords(JobIntent.COMPLETE).onlyCommandRejections().await();

    // then
    assertBaseline("rejections", startPosition);
  }

  private void deploy(final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
  }

  private long createProcessInstance() {
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(CLAIMS);
  }

  private static long jobKey(final long processInstanceKey, final String type) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(type)
        .getFirst()
        .getKey();
  }

  private void completeJob(final long jobKey) {
    writeJobCommand(jobKey, JobIntent.COMPLETE, new JobRecord());
  }

  /** Writes the job command as a worker sends it: with the claims of an OIDC client. */
  private void writeJobCommand(final long jobKey, final JobIntent intent, final JobRecord value) {
    engine.writeRecords(RecordToWrite.commandFromRequest(CLAIMS).job(intent, value).key(jobKey));
  }

  private static void awaitCompletion(final long processInstanceKey) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private long lastPosition() {
    return engine.events().map(LoggedEvent::getPosition).reduce((a, b) -> b).orElse(-1L);
  }

  private void assertBaseline(final String scenario, final long startPosition) {
    final var actual = measure(startPosition);
    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      writeBaseline(scenario, actual);
      return;
    }
    assertThat(changes(readBaseline(scenario), actual))
        .as(
            "The log bytes changed by more than %d%%. Read the Javadoc of %s before you update"
                + " the baseline",
            TOLERANCE_PERCENT, getClass().getSimpleName())
        .isEmpty();
  }

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
              if (TIMED_VALUE_TYPES.contains(metadata.getValueType())) {
                return;
              }
              final var group = metadata.getRecordType() + " " + metadata.getValueType();
              final int bytes = event.getMetadataLength() + event.getValueLength();
              sizes.merge(group, new Size(1, bytes), Size::plus);
            });
    return sizes;
  }

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

  private static long lowerBound(final long bytes) {
    return bytes * (100 - TOLERANCE_PERCENT) / 100;
  }

  private static long upperBound(final long bytes) {
    return bytes * (100 + TOLERANCE_PERCENT) / 100;
  }

  /** Claims of an OIDC client with a realistic size, as the follow-up records copy them. */
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

  private static Map<String, Size> readBaseline(final String scenario) {
    try {
      final var sizes = new TreeMap<String, Size>();
      for (final var line : Files.readAllLines(baselinePath(scenario), StandardCharsets.UTF_8)) {
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
      throw new UncheckedIOException("Failed to read the baseline " + baselinePath(scenario), e);
    }
  }

  private static void writeBaseline(final String scenario, final Map<String, Size> sizes) {
    final var lines = new ArrayList<String>();
    lines.add("# Written by RecordSizeBaselineTest. Do not edit by hand.");
    lines.add("# <record type> <value type> <record count> <bytes of metadata and value>");
    sizes.forEach((group, size) -> lines.add(group + " " + size.count() + " " + size.bytes()));
    try {
      Files.createDirectories(baselinePath(scenario).getParent());
      Files.write(baselinePath(scenario), lines, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write the baseline " + baselinePath(scenario), e);
    }
  }

  /** Surefire runs the tests in the module directory. */
  private static Path baselinePath(final String scenario) {
    var dir = Path.of("src", "test", "resources");
    for (final var segment : RecordSizeBaselineTest.class.getPackageName().split("\\.")) {
      dir = dir.resolve(segment);
    }
    return dir.resolve(BASELINE_FOLDER).resolve(scenario + ".txt");
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
