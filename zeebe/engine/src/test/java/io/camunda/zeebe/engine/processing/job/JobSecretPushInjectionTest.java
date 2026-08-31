/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.Counter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the secret resolution of the job-push activation path: a job is pushed only once every
 * secret reference of its input mappings has a cached value, the pushed job carries the values, and
 * a job parked for an uncached reference is pushed as soon as the background resolution completes.
 */
public final class JobSecretPushInjectionTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_NAME = "token";
  private static final String SECRET_VALUE = "resolved-secret";

  /**
   * Jobs parked on one reference, padded so that their hand-outs cannot share a single record
   * batch: the padding sits in a task header, which is part of the job record the reactivation
   * sizes its batch against, and ten of them exceed the 4 MB maximum fragment size.
   */
  private static final int PADDED_JOB_COUNT = 10;

  private static final int JOB_PADDING_SIZE = 500_000;

  /**
   * Jobs parked on one reference and handed out to a stream whose worker name is written twice per
   * hand-out: the reserve leaves 8 KB of slack per job, so a name this size overruns it by two
   * orders of magnitude and a handful of hand-outs exceed the 4 MB maximum fragment size.
   */
  private static final int OVERSIZED_WORKER_JOB_COUNT = 10;

  private static final int OVERSIZED_WORKER_NAME_SIZE = 300_000;

  // static, because the engine rule below reads them while it is initialized, and a field
  // initializer can only read a field declared before it; setUp resets them per test
  private static final Map<String, String> CACHED_SECRETS = new ConcurrentHashMap<>();
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  /**
   * The background resolution is driven by hand: the tests write the {@code RESOLUTION_COMPLETE}
   * and {@code RESOLUTION_FAIL} commands the scheduler would write, and cache the value it would
   * cache, so what the push path does with a resolution outcome is tested without depending on the
   * timing of the scheduler. Its interval is set beyond the lifetime of a test so it never
   * interferes.
   */
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withJobStreamer(JOB_STREAMER)
          .withSecretStoreRegistry(
              new SecretStoreRegistry(
                  Map.of("default", new NoopSecretStore()),
                  Map.of("default", new TestSecretCache())))
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofHours(1)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private RecordingJobStream jobStream;

  @Before
  public void setUp() {
    // the cache and the streamer outlive a single test, so each test starts from an empty cache,
    // no stream and no recorded notification
    CACHED_SECRETS.clear();
    JOB_STREAMER.clearStreams();
    JOB_STREAMER.clearNotifications();
    jobStream = registerJobStream();
  }

  @Test
  public void shouldPushJobWithResolvedSecretValue() {
    // given
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - the pushed job carries the resolved value
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(jobStream.getActivatedJobs()).hasSize(1);
    assertThat(pushedJob.jobRecord().getVariables())
        .containsEntry("authorization", "Bearer " + SECRET_VALUE);

    // and - the activation event that carries the job onto the log holds no variables at all
    final Record<JobBatchRecordValue> activated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).getFirst();
    assertThat(activated.getValue().getJobs()).hasSize(1);
    assertThat(activated.getValue().getJobs().getFirst().getVariables()).isEmpty();

    // and - no exported record leaks the resolved secret value
    assertNoRecordLeaksTheSecretValue();
  }

  @Test
  public void shouldNotPushJobWhenSecretIsNotCached() {
    // given - the secret has no cached value and the store cannot resolve it either
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);

    // then - the resolution is requested for the job and the job is parked
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference(SECRET_NAME)
            .getFirst();
    assertThat(requested.getValue().getJobKeys()).containsExactly(jobKey);
    assertThat(jobState(jobKey)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);

    // and - the job is neither activated nor pushed: the process instance runs no further, so
    // waiting for the element instance the job belongs to bounds what can still be exported
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(TASK_ID)
        .getFirst();
    assertThat(jobStream.getActivatedJobs()).isEmpty();
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getIntent() == JobBatchIntent.ACTIVATED);

    // and - the skip is reported on its own action. Panel 13 of monitor/grafana/zeebe.json
    // highlights action="skipped" orange to warn operators about unleased workers colliding with
    // leased jobs, so an ordinary secret-cache miss must not land there
    assertThat(findJobCounter("skipped uncached secret"))
        .hasValueSatisfying(counter -> assertThat(counter.count()).isOne());
    assertThat(findJobCounter("skipped")).isEmpty();
  }

  @Test
  public void shouldPushJobAfterItsSecretResolves() {
    // given - a job parked for an uncached reference
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    awaitResolutionRequests(1);

    // when - the background resolution caches the value and completes
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();

    // then - the job is pushed exactly once, with the resolved value
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(pushedJob.jobKey()).isEqualTo(jobKey);
    assertThat(pushedJob.jobRecord().getVariables())
        .containsEntry("authorization", "Bearer " + SECRET_VALUE);
    final Record<JobBatchRecordValue> activated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).getFirst();
    assertThat(activated.getValue().getJobKeys()).containsExactly(jobKey);
    assertThat(jobStream.getActivatedJobs()).hasSize(1);

    // and - no exported record leaks the resolved secret value
    assertNoRecordLeaksTheSecretValue();
  }

  @Test
  public void shouldPushEveryJobWaitingOnTheSameReference() {
    // given - two jobs parked for the same uncached reference
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long firstJobKey =
        jobKeyOf(engine.processInstance().ofBpmnProcessId(PROCESS_ID).create());
    final long secondJobKey =
        jobKeyOf(engine.processInstance().ofBpmnProcessId(PROCESS_ID).create());
    awaitResolutionRequests(2);

    // when - the reference resolves
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();

    // then - both jobs are pushed with the resolved value
    Awaitility.await("until both waiting jobs are pushed")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(2));
    assertThat(jobStream.getActivatedJobs())
        .extracting(ActivatedJob::jobKey)
        .containsExactlyInAnyOrder(firstJobKey, secondJobKey);
    assertThat(jobStream.getActivatedJobs())
        .allSatisfy(
            job ->
                assertThat(job.jobRecord().getVariables())
                    .containsEntry("authorization", "Bearer " + SECRET_VALUE));
  }

  @Test
  public void shouldNotPushJobWhenItsSecretFailsToResolve() {
    // given - a job parked for a reference the store has no value for
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    awaitResolutionRequests(1);

    // when - the resolution fails permanently
    failResolution();

    // then - the job gets a secret resolution incident and stays parked and unpushed
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.SECRET_RESOLUTION_ERROR);
    assertThat(jobStream.getActivatedJobs()).isEmpty();
    assertThat(jobState(jobKey)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);
  }

  @Test
  public void shouldNameTheMismatchedReferenceWhenInjectionFailsOnPush() {
    // given - the default tenant, required for a tenant-scoped cluster variable and for the job
    // that belongs to it to be looked up later
    engine.tenant().newTenant().withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER).create();

    // and - a SECRET_REFERENCE cluster variable that currently points at "tokenA"
    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.tokenA"))
        .create();

    // and - a service task whose start execution listener opens a window between the input
    // mapping baking the *then-current* placeholder ("tokenA") into the job's variables and the
    // job's creation, which resolves the reference from whatever the cluster variable holds by then
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("mismatch-process")
            .startEvent()
            .serviceTask(
                "mismatch-task",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeStartExecutionListener("mismatch-start-el")
                        .zeebeInputExpression("camunda.vars.tenant.creds.token", "authToken"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("mismatch-process").create();
    final long listenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("mismatch-start-el")
            .getFirst()
            .getKey();

    // and - inside that window the reference is repointed at "tokenB", whose value is cached
    // before the job becomes activatable, so the push path actually attempts (and fails) the
    // replacement instead of parking the job on an uncached reference
    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .withValue(Map.of("token", "camunda.secrets.tokenB"))
        .update();
    CACHED_SECRETS.put("tokenB", "resolved-B");

    // when - completing the listener lets the job be created with a "tokenB" reference while its
    // variables still literally hold "camunda.secrets.tokenA", and the push path injects it
    engine.job().withKey(listenerJobKey).complete();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();

    // then - the incident names the mismatched placeholder and its JSON pointer, the same detail
    // the batch path gives, instead of the cause-neutral fallback message
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.SECRET_RESOLUTION_ERROR);
    assertThat(incident.getValue().getErrorMessage())
        .contains(
            "the secret reference 'camunda.secrets.tokenB' could not be resolved at '/authToken'")
        .contains("Fix the variable's value or the input mapping that sets it");

    // and - the job is not pushed, and no exported record leaks the resolved value
    assertThat(jobStream.getActivatedJobs()).isEmpty();
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getValue().toString().contains("resolved-B"));
  }

  @Test
  public void shouldRequestResolutionAgainWhenTheIncidentIsResolved() {
    // given - a job with an incident for a reference that failed to resolve
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    awaitResolutionRequests(1);
    failResolution();
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();

    // when - the operator resolves the incident, which hands the job to this path again
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then - the resolution of the still uncached reference is requested a second time
    final var requests = awaitResolutionRequests(2);
    assertThat(requests)
        .allSatisfy(record -> assertThat(record.getValue().getJobKeys()).containsExactly(jobKey));
    assertThat(jobStream.getActivatedJobs()).isEmpty();

    // and - once that resolution completes, the job is pushed with the value
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(pushedJob.jobKey()).isEqualTo(jobKey);
    assertThat(pushedJob.jobRecord().getVariables())
        .containsEntry("authorization", "Bearer " + SECRET_VALUE);
  }

  @Test
  public void shouldRequestResolutionOfEveryUncachedReferenceOfTheJob() {
    // given - a job with two uncached references, one of them used at two paths
    deploy(
        t ->
            t.zeebeInputExpression("camunda.secrets.token", "a")
                .zeebeInputExpression("camunda.secrets.token", "b")
                .zeebeInputExpression("camunda.secrets.apiKey", "c"));

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);

    // then - one request per reference, each naming the job once, and no push
    final var requests =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .limit(2)
            .asList();
    assertThat(requests)
        .extracting(record -> record.getValue().getSecretReference())
        .containsExactlyInAnyOrder(SECRET_NAME, "apiKey");
    assertThat(requests)
        .allSatisfy(record -> assertThat(record.getValue().getJobKeys()).containsExactly(jobKey));
    assertThat(jobStream.getActivatedJobs()).isEmpty();
  }

  @Test
  public void shouldPushJobOnlyOnceEveryReferenceIsCached() {
    // given - a job with two references, only one of them cached
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    deploy(
        t ->
            t.zeebeInputExpression("camunda.secrets.token", "a")
                .zeebeInputExpression("camunda.secrets.apiKey", "b"));
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    awaitResolutionRequests(1, "apiKey");
    assertThat(jobStream.getActivatedJobs()).isEmpty();

    // when - the second reference resolves too
    CACHED_SECRETS.put("apiKey", "resolved-api-key");
    completeResolution("apiKey");

    // then - the job is pushed with both values
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(pushedJob.jobKey()).isEqualTo(jobKey);
    assertThat(pushedJob.jobRecord().getVariables())
        .containsEntry("a", SECRET_VALUE)
        .containsEntry("b", "resolved-api-key");
  }

  @Test
  public void shouldPushJobWithoutSecretReferencesUnchanged() {
    // given
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    deploy(t -> t.zeebeInputExpression("\"plain-value\"", "authorization"));

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - the job is pushed with its variables unchanged and without any resolution request
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(pushedJob.jobRecord().getVariables()).containsEntry("authorization", "plain-value");
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getValueType() == ValueType.SECRET_REFERENCE);
  }

  @Test
  public void shouldOnlyNotifyWorkersWhenNoStreamIsRegistered() {
    // given - no job stream for the job type, so the job is not pushed but only announced
    JOB_STREAMER.clearStreams();
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);

    // then - the job stays activatable for a long poll, which owns the check on that path; the
    // resolution is nonetheless requested right away as a warmup, without parking the job or
    // naming its key (see SecretResolutionWarmupTest)
    Awaitility.await("until the workers are notified")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(JOB_STREAMER.notificationsForJob(JOB_TYPE)).isPositive());
    assertThat(jobState(jobKey)).isEqualTo(State.ACTIVATABLE);
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference(SECRET_NAME)
            .getFirst();
    assertThat(requested.getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldNotifyWorkersOncePerJobTypeWhenReactivatedJobsCannotBePushed() {
    // given - two jobs parked for the same reference, and no job stream left to push them to
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    awaitResolutionRequests(2);
    JOB_STREAMER.clearStreams();

    // when - the reference resolves and both jobs become activatable again
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .withSecretReference(SECRET_NAME)
        .getFirst();

    // then - the workers of the job type are notified once for the whole batch, not once per job
    Awaitility.await("until the workers are notified")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(JOB_STREAMER.notificationsForJob(JOB_TYPE)).isEqualTo(1));
    assertThat(jobStream.getActivatedJobs()).isEmpty();
  }

  /**
   * A lease-skip counting regression, but this suite is the only place that can reproduce it:
   * {@code SecretReferenceBatchReactivateJobsProcessor} reactivating jobs a resolved secret parked
   * together is the only production path that demotes several same-type leased jobs within one
   * shared-notification batch. Every trigger {@link ActivatableJobsPushWithLeaseTest} covers
   * processes a single job per command, so none of them can build that precondition.
   */
  @Test
  public void shouldCountEveryLeaseSkipWhenABatchDemotesJobsOfTheSameType() {
    // given - two jobs already leased via a leasing stream, each carrying its own lease token
    JOB_STREAMER.clearStreams();
    final RecordingJobStream leasingStream = registerLeasingJobStream();
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long firstJobKey =
        jobKeyOf(engine.processInstance().ofBpmnProcessId(PROCESS_ID).create());
    final long secondJobKey =
        jobKeyOf(engine.processInstance().ofBpmnProcessId(PROCESS_ID).create());
    Awaitility.await("until both jobs are leased on the leasing stream")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(leasingStream.getActivatedJobs()).hasSize(2));
    final String firstToken = leaseTokenOf(leasingStream, firstJobKey);
    final String secondToken = leaseTokenOf(leasingStream, secondJobKey);

    // and - each job parks for secret resolution again once its cached value is evicted
    CACHED_SECRETS.remove(SECRET_NAME);
    engine.job().withKey(firstJobKey).withLeaseToken(firstToken).withRetries(3).fail();
    engine.job().withKey(secondJobKey).withLeaseToken(secondToken).withRetries(3).fail();
    awaitResolutionRequests(2);

    // and - only a non-leasing stream remains registered by the time the reference resolves
    JOB_STREAMER.clearStreams();
    final RecordingJobStream nonLeasingStream = registerJobStream();
    final double skippedBefore = skippedMetric();

    // when - the reference resolves, reactivating both jobs through one shared-notification batch
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .withSecretReference(SECRET_NAME)
        .getFirst();

    // then - the batch demotes both jobs from push to notify-only, and each must count on its own
    Awaitility.await("until the skip signal accounts for both demoted jobs")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(skippedMetric() - skippedBefore)
                    .describedAs(
                        "a batch that demotes several same-type leased jobs must count every "
                            + "demotion, not only the first job of the type")
                    .isEqualTo(2));
    assertThat(nonLeasingStream.getActivatedJobs())
        .describedAs("neither leased job may be pushed to a non-leasing stream")
        .isEmpty();
  }

  /**
   * A reactivation sizes its record batch against what handing each job out will write into that
   * same batch, but the sizing and the writing live in different classes. The processor's own test
   * mocks the writer, so it can only check the arithmetic; this drives the chain on a real record
   * batch with jobs too large to share one, which is what turns the reserved bound into a checked
   * one.
   */
  @Test
  public void shouldPushEveryReactivatedJobWithoutExceedingTheRecordBatch() {
    // given - parked jobs padded so that one record batch cannot hand out all of them
    deploy(
        t -> {
          t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization");
          t.zeebeTaskHeader("padding", "x".repeat(JOB_PADDING_SIZE));
        });
    for (int i = 0; i < PADDED_JOB_COUNT; i++) {
      engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    }
    awaitResolutionRequests(PADDED_JOB_COUNT);

    // when - the reference resolves
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();

    // then - every parked job is handed out, none left behind by the size cut
    Awaitility.await("until every parked job is pushed")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(PADDED_JOB_COUNT));

    // and - no batch overflowed; that rejection strands the jobs of its cycle for good, since the
    // reactivation command is written by the engine and has nobody to report the failure to
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getRejectionType() == RejectionType.EXCEEDED_BATCH_RECORD_SIZE);

    // and - the hand-outs really spanned several batches, so the cut was exercised rather than the
    // jobs happening to fit one of them
    assertThat(exportedCountOf(SecretReferenceIntent.BATCH_JOBS_REACTIVATED))
        .describedAs("the padded jobs do not fit one batch, so the chain runs several cycles")
        .isGreaterThan(1);
  }

  /**
   * A reactivation sizes its batch against the stored job records, which carry no worker name: that
   * name belongs to the stream that takes the job, and a hand-out writes it twice, on the job it
   * puts in the activation batch and on the batch itself. A stream registered with a worker name
   * larger than the per-job slack therefore makes every hand-out cost more than the batch reserved
   * for it, and the reserve alone cannot keep the cycle inside the maximum fragment size.
   */
  @Test
  public void shouldNotStrandReactivatedJobsWhenTheWorkerNameOutgrowsTheReserve() {
    // given - a stream whose worker name dwarfs the slack the reserve leaves per job, and jobs
    // parked on one reference
    JOB_STREAMER.clearStreams();
    jobStream = registerJobStream("w".repeat(OVERSIZED_WORKER_NAME_SIZE));
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final List<Long> jobKeys = parkJobsOnTheSecret(OVERSIZED_WORKER_JOB_COUNT);

    // when - the reference resolves and the reactivation hands the parked jobs out
    CACHED_SECRETS.put(SECRET_NAME, SECRET_VALUE);
    completeResolution();
    // an overflowing cycle writes no batch event at all, so waiting for one would only time out;
    // waiting for either outcome lets the assertions below name what actually went wrong
    Awaitility.await("until the reactivation cycle has run")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        exportedCountOf(SecretReferenceIntent.BATCH_JOBS_REACTIVATED) > 0
                            || exportedBatchOverflowCount() > 0)
                    .isTrue());

    // then - no batch overflowed; that rejection rolls the cycle back, and since the reactivation
    // command is written by the engine there is nobody to report it to
    assertThat(exportedBatchOverflowCount()).isZero();

    // and - the reactivation handed out what it could
    Awaitility.await("until the reactivated jobs are pushed")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).isNotEmpty());

    // and - no job is left waiting on a reference that is resolved already. A job the batch could
    // not take stays activatable, so a long poll still collects it; a job left parked waits for a
    // resolution that will not be requested again
    assertThat(jobKeys)
        .allSatisfy(
            jobKey ->
                assertThat(jobState(jobKey)).isNotEqualTo(State.WAITING_FOR_SECRET_RESOLUTION));
  }

  /** Counts the record batches rejected for exceeding the maximum fragment size. */
  private long exportedBatchOverflowCount() {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getRejectionType() == RejectionType.EXCEEDED_BATCH_RECORD_SIZE)
        .count();
  }

  /** Creates {@code count} instances whose jobs all park on the test's secret reference. */
  private List<Long> parkJobsOnTheSecret(final int count) {
    final List<Long> jobKeys = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      jobKeys.add(jobKeyOf(engine.processInstance().ofBpmnProcessId(PROCESS_ID).create()));
    }
    awaitResolutionRequests(count);
    return jobKeys;
  }

  /** Counts the records exported so far, without waiting for more of them to arrive. */
  private long exportedCountOf(final SecretReferenceIntent intent) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == intent)
        .count();
  }

  /**
   * Asserts no exported record carries the resolved value. The record values are scanned rather
   * than the records, since a record's own {@code toString} is truncated and could hide a value.
   */
  private void assertNoRecordLeaksTheSecretValue() {
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getValue().toString().contains(SECRET_VALUE));
  }

  private ActivatedJob awaitPushedJob() {
    Awaitility.await("until the job is pushed to the stream")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).isNotEmpty());
    return jobStream.getActivatedJobs().getFirst();
  }

  private List<Record<SecretReferenceRecordValue>> awaitResolutionRequests(final int count) {
    return awaitResolutionRequests(count, SECRET_NAME);
  }

  private List<Record<SecretReferenceRecordValue>> awaitResolutionRequests(
      final int count, final String secretName) {
    return RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
        .withSecretReference(secretName)
        .limit(count)
        .asList();
  }

  /** Writes the command the resolution scheduler writes once it cached the value. */
  private void completeResolution() {
    completeResolution(SECRET_NAME);
  }

  private void completeResolution(final String secretName) {
    engine.writeRecords(
        RecordToWrite.command()
            .secretReference(
                SecretReferenceIntent.RESOLUTION_COMPLETE,
                new SecretReferenceRecord()
                    .setSecretReference(secretName)
                    .setResolutionState(ResolutionState.SUCCESS)));
  }

  /** Writes the command the resolution scheduler writes when a reference cannot be resolved. */
  private void failResolution() {
    engine.writeRecords(
        RecordToWrite.command()
            .secretReference(
                SecretReferenceIntent.RESOLUTION_FAIL,
                new SecretReferenceRecord()
                    .setSecretReference(SECRET_NAME)
                    .setResolutionState(ResolutionState.NOT_FOUND)));
  }

  private RecordingJobStream registerJobStream() {
    return registerJobStream("test");
  }

  private RecordingJobStream registerJobStream(final String workerName) {
    final var worker = BufferUtil.wrapString(workerName);
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    return JOB_STREAMER.addJobStream(BufferUtil.wrapString(JOB_TYPE), properties);
  }

  private RecordingJobStream registerLeasingJobStream() {
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setWithLease(true);
    return JOB_STREAMER.addJobStream(BufferUtil.wrapString(JOB_TYPE), properties);
  }

  private String leaseTokenOf(final RecordingJobStream jobStream, final long jobKey) {
    return jobStream.getActivatedJobs().stream()
        .filter(activatedJob -> activatedJob.jobKey() == jobKey)
        .findFirst()
        .orElseThrow()
        .jobRecord()
        .getLeaseToken();
  }

  private double skippedMetric() {
    // the counter is only registered once the first lease-skip fires, so a read taken before any
    // job has been demoted for a lease collision must treat "not registered yet" as zero rather
    // than fail: shouldCountEveryLeaseSkipWhenABatchDemotesJobsOfTheSameType reads this as a
    // before/after delta, and its "before" snapshot runs before that first demotion happens
    return findJobCounter("skipped").map(Counter::count).orElse(0.0);
  }

  private long jobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private State jobState(final long jobKey) {
    return engine.getProcessingState().getJobState().getState(jobKey);
  }

  private Optional<Counter> findJobCounter(final String action) {
    return Optional.ofNullable(
        engine
            .getMeterRegistry()
            .find("zeebe.job.events.total")
            .tag("action", action)
            .tag("partition", "1")
            .tag("type", JOB_TYPE)
            .tag("job_kind", JobKind.BPMN_ELEMENT.name())
            .counter());
  }

  private void deploy(final Consumer<ServiceTaskBuilder> modifier) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ID,
                t -> {
                  t.zeebeJobType(JOB_TYPE);
                  modifier.accept(t);
                })
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();
  }

  /** Serves the test's cached secrets, and takes the values the background resolution caches. */
  private static final class TestSecretCache implements SecretCache {
    @Override
    public Optional<String> get(final String name) {
      return Optional.ofNullable(CACHED_SECRETS.get(name));
    }

    @Override
    public void put(final String name, final String value) {
      CACHED_SECRETS.put(name, value);
    }

    @Override
    public void remove(final String name) {
      CACHED_SECRETS.remove(name);
    }
  }
}
