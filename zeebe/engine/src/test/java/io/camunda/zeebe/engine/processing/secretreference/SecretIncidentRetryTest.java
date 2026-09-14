/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers what a {@code SECRET_RESOLUTION_ERROR} incident does when it is resolved (the "Retry"
 * button in Operate, and the {@code RESOLVE_INCIDENT} batch operation) while the secret is still
 * missing from the store.
 *
 * <p>Resolving such an incident only reactivates the job and pushes it to whatever job stream is
 * connected ({@code IncidentResolveProcessor#publishIncidentRelatedJob}). Secret resolution is
 * requested exclusively from the two activation paths, so with no consumer attached nothing
 * re-reads the store, no incident returns, and the instance reads as healthy while the secret is
 * still absent. See <a href="https://github.com/camunda/camunda/issues/62727">#62727</a>.
 *
 * <p>No test here registers a job stream, and none activates again after the resolve unless it says
 * so — that absence is the condition under test, not an omission. An activation would itself
 * re-check the references and park the job again, which is precisely why it must not be the only
 * thing that does. {@code
 * JobSecretPushInjectionTest#shouldRequestResolutionAgainWhenTheIncidentIsResolved} is the same
 * scenario with a stream registered, and passes: {@code publishWork} hands the job straight back to
 * the push path, which requests the resolution itself. That contrast is the bug — whether a retry
 * re-checks anything depends on a worker being attached, which is also why the issue's documented
 * workaround is to keep one connected.
 *
 * <p>Counts of incidents and resolution requests are read off {@link RecordingExporter} as recorded
 * now and awaited with Awaitility, rather than through a blocking {@code limit(n)} query. A count
 * that never reaches its target is the expected outcome of several of these tests, and a blocking
 * query answers that with a generic "timed out waiting for records" instead of naming the count it
 * got.
 */
public final class SecretIncidentRetryTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_NAME = "token";
  private static final String OTHER_SECRET_NAME = "otherToken";
  private static final String SECRET_VALUE = "resolved-secret";

  /** Covers a background resolution cycle and the incident round that follows it. */
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              // one-argument constructor: the registry puts a fresh, cold cache in front, so the
              // first activation parks the job instead of resolving it from the cache. It only
              // wraps the store, so the fields below are not read before they are initialized.
              new SecretStoreRegistry(
                  Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, new MutableMapSecretStore())))
          // the default interval is 5s, which every assertion below would otherwise wait on
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  /**
   * Backs the store of {@link #engine}. Mutable so a test can create the secret between the
   * incident and the retry, which is the one case where the retry is expected to succeed.
   */
  private final Map<String, String> storedSecrets = Collections.synchronizedMap(new HashMap<>());

  /** Counts store reads, so a test can tell "re-read and still missing" from "never re-read". */
  private final AtomicInteger storeReads = new AtomicInteger();

  @Test
  public void shouldRaiseIncidentAgainWhenSecretIsStillMissingAfterResolve() {
    // given - a job incidented because its secret does not exist, and no worker connected
    deploySingleTask();
    final long processInstanceKey = createInstanceAndParkItsJob();
    final long incidentKey = awaitSecretIncident(processInstanceKey).getKey();

    // when - the incident is resolved without the secret ever being created
    engine.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve();

    // then - a new incident is raised, because the retry re-reads the store and still finds nothing
    awaitSecretIncidentCount(processInstanceKey, 2);
    assertThat(secretIncidentKeysOf(processInstanceKey))
        .describedAs("the re-raised incident is a new one rather than the resolved one")
        .doesNotHaveDuplicates();
  }

  @Test
  public void shouldRequestResolutionAgainWhenSecretIncidentIsResolved() {
    // given
    deploySingleTask();
    final long processInstanceKey = createInstanceAndParkItsJob();
    awaitSecretIncident(processInstanceKey);

    // when
    engine.incident().ofInstance(processInstanceKey).resolve();

    // then - the resolve re-enters the resolution lifecycle rather than only reactivating the job
    Awaitility.await("until the resolution is requested a second time")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(resolutionRequestCountFor(SECRET_NAME))
                    .describedAs(
                        "resolution requests for '%s', without any activation having happened",
                        SECRET_NAME)
                    .isEqualTo(2));
  }

  @Test
  public void shouldReadTheStoreAgainWhenSecretIncidentIsResolved() {
    // given - the reads taken while the first incident was raised
    deploySingleTask();
    final long processInstanceKey = createInstanceAndParkItsJob();
    awaitSecretIncident(processInstanceKey);
    final int readsBeforeResolve = storeReads.get();

    // when
    engine.incident().ofInstance(processInstanceKey).resolve();

    // then - the retry actually goes back to the store instead of trusting the resolved record.
    // Asserted on the store itself because a retry could re-raise from stale state and still be
    // wrong: the point of the retry is the re-read.
    Awaitility.await("until the store is read again")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(storeReads.get())
                    .describedAs("store reads after the retry")
                    .isGreaterThan(readsBeforeResolve));
  }

  @Test
  public void shouldNotRaiseIncidentAgainWhenSecretWasCreatedBeforeResolve() {
    // given - the operator fixes the actual problem before clicking retry
    deploySingleTask();
    final long processInstanceKey = createInstanceAndParkItsJob();
    awaitSecretIncident(processInstanceKey);
    storedSecrets.put(SECRET_NAME, SECRET_VALUE);

    // when
    engine.incident().ofInstance(processInstanceKey).resolve();

    // then - the job is handed out with the secret and the instance finishes. The activation below
    // is what re-reads the store today; once the resolve does it too the reactivation has already
    // happened by then, so awaiting it keeps this green either way rather than pinning the test to
    // which of the two triggered the read.
    engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .withSecretReference(SECRET_NAME)
        .getFirst();
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(3).withRequestId(3L).activate();
    assertThat(activated.getValue().getJobKeys()).hasSize(1);
    engine.job().withKey(activated.getValue().getJobKeys().getFirst()).complete();

    // and - the instance reaching its end bounds the count below, so no incident can still be on
    // its way when it is read
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
    assertThat(secretIncidentsOf(processInstanceKey))
        .describedAs("a retry after the secret exists resolves the instance for good")
        .hasSize(1);
  }

  @Test
  public void shouldRaiseIncidentAgainForEveryParallelJobBlockedOnTheSameSecret() {
    // given - the shape from the issue: two parallel service tasks, one missing secret, one
    // incident each
    deployTwoParallelTasks();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    parkJobsOf(processInstanceKey);
    awaitSecretIncidentCount(processInstanceKey, 2);

    // when - both are retried, as the Operate UI does per incident
    secretIncidentKeysOf(processInstanceKey)
        .forEach(
            incidentKey ->
                engine.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve());

    // then - both come back; neither job is left silently activatable
    awaitSecretIncidentCount(processInstanceKey, 4);
  }

  @Test
  public void shouldRaiseIncidentAgainForSecondSecretOfJobWithTwoMissingReferences() {
    // given - a job blocked on two missing secrets. Only one incident is raised for it, so the
    // retry must re-request every unresolved reference and not just the incidented one; otherwise
    // the job parks on the first and the second is never asked for again.
    deployTaskWithTwoSecrets();
    final long processInstanceKey = createInstanceAndParkItsJob();
    awaitSecretIncident(processInstanceKey);

    // when
    engine.incident().ofInstance(processInstanceKey).resolve();

    // then
    Awaitility.await("until the second reference is requested again")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(resolutionRequestCountFor(OTHER_SECRET_NAME))
                    .describedAs("resolution requests for the job's other reference")
                    .isEqualTo(2));
  }

  @Test
  public void shouldRaiseIncidentAgainWhenTheBatchOperationRetryFixesNothing() {
    // given - the bulk "Retry" from the Processes list, which reports every item completed
    deploySingleTask();
    final long processInstanceKey = createInstanceAndParkItsJob();
    final long incidentKey = awaitSecretIncident(processInstanceKey).getKey();

    // when - the two commands ResolveIncidentBatchOperationExecutor emits for a job incident are
    // driven directly. The batch machinery is not stood up here because it carries its own engine
    // fixture with no secret store; what distinguishes the bulk path is only this command pair,
    // and a batch item counts as completed once the RESOLVE is accepted.
    engine.job().withKey(jobKeyOf(processInstanceKey)).withRetries(1).updateRetries();
    engine.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve();

    // then
    awaitSecretIncidentCount(processInstanceKey, 2);
  }

  @Test
  public void shouldNotRequestResolutionWhenResolvingIncidentOfJobWithoutSecrets() {
    // given - an ordinary job incident, which must keep taking the publishWork path untouched
    deployTaskWithoutSecret();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    engine.jobs().withType(JOB_TYPE).activate();
    engine.job().withKey(jobKey).withRetries(0).fail();
    final long incidentKey = awaitAnyIncident(processInstanceKey).getKey();
    engine.job().withKey(jobKey).withRetries(1).updateRetries();

    // when
    engine.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve();

    // then - the instance runs to completion, which bounds the read below. Bounding on the RESOLVED
    // record instead would close the window before the point of the assertion: any resolution this
    // path wrongly requested is appended after it, and so would never be looked at.
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(activated.getValue().getJobKeys()).containsExactly(jobKey);
    engine.job().withKey(jobKey).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();

    assertThat(RecordingExporter.getRecords())
        .describedAs("a non-secret incident triggers no secret resolution")
        .noneMatch(record -> record.getValueType() == ValueType.SECRET_REFERENCE);
  }

  /** The secret incidents raised for the instance, as recorded now — never waits for one more. */
  private List<IncidentRecordValue> secretIncidentsOf(final long processInstanceKey) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == IncidentIntent.CREATED)
        .map(Record::getValue)
        .filter(IncidentRecordValue.class::isInstance)
        .map(IncidentRecordValue.class::cast)
        .filter(incident -> incident.getProcessInstanceKey() == processInstanceKey)
        .filter(incident -> incident.getErrorType() == ErrorType.SECRET_RESOLUTION_ERROR)
        .toList();
  }

  private List<Long> secretIncidentKeysOf(final long processInstanceKey) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == IncidentIntent.CREATED)
        .filter(
            record ->
                record.getValue() instanceof final IncidentRecordValue incident
                    && incident.getProcessInstanceKey() == processInstanceKey
                    && incident.getErrorType() == ErrorType.SECRET_RESOLUTION_ERROR)
        .map(Record::getKey)
        .toList();
  }

  private void awaitSecretIncidentCount(final long processInstanceKey, final int count) {
    Awaitility.await("until %d secret incidents are raised".formatted(count))
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(secretIncidentsOf(processInstanceKey))
                    .describedAs("secret incidents raised for instance %d", processInstanceKey)
                    .hasSize(count));
  }

  /** How often the resolution of the reference was requested, as recorded now. */
  private long resolutionRequestCountFor(final String secretName) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_REQUESTED)
        .filter(
            record ->
                record.getValue() instanceof final SecretReferenceRecordValue value
                    && secretName.equals(value.getSecretReference()))
        .count();
  }

  private long jobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private Record<IncidentRecordValue> awaitSecretIncident(final long processInstanceKey) {
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
        .getFirst();
  }

  private Record<IncidentRecordValue> awaitAnyIncident(final long processInstanceKey) {
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  /**
   * Creates an instance and activates once, which parks its job on the uncached secret and requests
   * the background resolution — the single activation request from the issue's reproduction.
   */
  private long createInstanceAndParkItsJob() {
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    parkJobsOf(processInstanceKey);
    return processInstanceKey;
  }

  private void parkJobsOf(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();
    assertThat(activated.getValue().getJobs())
        .describedAs("the job is withheld and parked, because its secret is not cached")
        .isEmpty();
  }

  private void deploySingleTask() {
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets." + SECRET_NAME, "authorization"))
            .endEvent()
            .done());
  }

  private void deployTaskWithTwoSecrets() {
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets." + SECRET_NAME, "authorization")
                        .zeebeInputExpression("camunda.secrets." + OTHER_SECRET_NAME, "apiKey"))
            .endEvent()
            .done());
  }

  private void deployTaskWithoutSecret() {
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done());
  }

  private void deployTwoParallelTasks() {
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(
                "task-a",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets." + SECRET_NAME, "authorization"))
            .parallelGateway("join")
            .endEvent()
            .moveToNode("fork")
            .serviceTask(
                "task-b",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets." + SECRET_NAME, "authorization"))
            .connectTo("join")
            .done());
  }

  private void deploy(final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
  }

  /**
   * Answers from {@link #storedSecrets}, which a test may change between the incident and the
   * retry, and records every read so a test can assert the retry went back to the store.
   */
  private final class MutableMapSecretStore implements SecretStore {

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      storeReads.incrementAndGet();
      return names.stream()
          .collect(
              Collectors.toMap(
                  name -> name,
                  name ->
                      Optional.ofNullable(storedSecrets.get(name))
                          .<SecretResolutionResult>map(SecretResolutionResult.Resolved::new)
                          .orElseGet(
                              () ->
                                  new SecretResolutionResult.Failed(
                                      SecretErrorCode.NOT_FOUND, "no such secret", null))));
    }

    @Override
    public List<String> list() {
      return List.copyOf(storedSecrets.keySet());
    }
  }
}
