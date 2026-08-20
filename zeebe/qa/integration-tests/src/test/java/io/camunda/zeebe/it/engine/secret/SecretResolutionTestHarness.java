/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.awaitility.Awaitility;

/**
 * The harness the secret resolution ITs share: the broker they all configure the same way, the
 * process and instances they all deploy and create, and the exporter queries they all assert on.
 *
 * <p>Three of those queries are the reason this exists rather than each test writing its own. A
 * record is exported after the command that wrote it was committed, so a client call returning is
 * no proof that the records of that call are in {@link RecordingExporter} yet: an assertion made
 * right after it may inspect a record set the activation has not reached. {@link
 * #awaitActivationExported} is the anchor for that, and every assertion about what an activation
 * did or did not write belongs behind it.
 */
public final class SecretResolutionTestHarness {

  /** Short enough that a test does not wait on the default 5s between background cycles. */
  public static final Duration RESOLUTION_INTERVAL = Duration.ofMillis(200);

  /** Has to outlive the park, the background resolution and the reactivation of a job. */
  public static final Duration LONG_POLL_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Long enough that an activated job never times out back into a later poll, including in a test
   * that moves the broker's clock forward to expire a cached value.
   */
  public static final Duration JOB_TIMEOUT = Duration.ofMinutes(30);

  /**
   * Covers a background cycle, an incident round and, for the suites that create many instances,
   * the polls that collect them all.
   */
  public static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(60);

  /** Keeps a priming poll from waiting for the job it is not there to collect. */
  public static final Duration PRIMING_POLL_TIMEOUT = Duration.ofSeconds(1);

  /** The variable a test's input mapping writes its secret into. */
  public static final String INPUT_TARGET = "token";

  /**
   * Enough process instance commands in flight to be quick, few enough that the broker's
   * backpressure does not reject them.
   */
  public static final int CREATION_WAVE_SIZE = 20;

  /**
   * The jobs one {@code BATCH_REACTIVATE_JOBS} command carries, mirroring {@code
   * SecretReferenceBatchReactivateJobsProcessor#MAX_BATCH_SIZE}: what makes the reactivation of
   * more jobs than this a chain of commands rather than one of them.
   */
  public static final int REACTIVATION_BATCH_SIZE = 100;

  private SecretResolutionTestHarness() {}

  /**
   * A broker with an empty file-based secret store, so each test writes the one secret it needs
   * under its own name, and a background resolution that does not keep tests waiting.
   */
  public static TestStandaloneBroker newBrokerWithEmptySecretStore() {
    return new TestStandaloneBroker()
        .withRecordingExporter(true)
        .withUnauthenticatedAccess()
        .withFileBasedSecretStore(directory -> {})
        .withProcessingConfig(
            processing -> processing.getEngine().getSecrets().setInterval(RESOLUTION_INTERVAL));
  }

  /**
   * Gives the record streams of a test the same budget as its other awaits. Belongs in a {@code
   * BeforeEach}: the {@code ZeebeIntegration} extension resets the recording exporter, and its
   * maximum wait time with it, before every test.
   */
  public static void applyRecordWaitTime() {
    RecordingExporter.setMaximumWaitTime(AWAIT_TIMEOUT.toMillis());
  }

  /**
   * A name no other test uses. The secret cache is per broker and shared with the gateway's secret
   * endpoints, so a name reused across tests runs against a warm cache and never exercises the
   * cache-miss path. A reference is a FEEL path, so the name is kept to a bare identifier that
   * needs no escaping; see {@link #uniqueHyphenatedSecretName()} for the escaped variant.
   */
  public static String uniqueSecretName() {
    return "s" + UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * The same, with a dash in it — the shape the backing stores routinely hold ({@code db-password},
   * {@code tls-key}). Only usable through {@link #backtickedSecretReference(String)}, since FEEL
   * reads a bare dash as the minus operator.
   */
  public static String uniqueHyphenatedSecretName() {
    return uniqueSecretName() + "-dashed";
  }

  public static String secretReference(final String secretName) {
    return "camunda.secrets." + secretName;
  }

  /**
   * A reference whose name is backtick-escaped, which is how a name FEEL does not accept as a bare
   * identifier — a dashed one above all — is written in an expression.
   */
  public static String backtickedSecretReference(final String secretName) {
    return secretReference("`" + secretName + "`");
  }

  /** Writes one secret of the broker's file-based store, creating or replacing its file. */
  public static void writeSecret(
      final TestStandaloneBroker broker, final String secretName, final String value) {
    try {
      Files.writeString(
          broker.getFileBasedSecretStoreDirectory().resolve(secretName),
          value,
          StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write the secret '" + secretName + "'", e);
    }
  }

  /** Deploys a process with one service task whose input mapping reads {@code inputExpression}. */
  public static void deployProcessWithInput(
      final CamundaClient client,
      final String processId,
      final String jobType,
      final String inputExpression) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    task ->
                        task.zeebeJobType(jobType)
                            .zeebeInputExpression(inputExpression, INPUT_TARGET))
                .endEvent()
                .done(),
            processId + ".bpmn")
        .send()
        .join();
  }

  public static long createProcessInstance(final CamundaClient client, final String processId) {
    return createProcessInstance(client, processId, Map.of());
  }

  public static long createProcessInstance(
      final CamundaClient client, final String processId, final Map<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  /**
   * Creates the instances in waves of {@link #CREATION_WAVE_SIZE} rather than all at once, so the
   * broker's backpressure does not reject them.
   */
  public static List<Long> createProcessInstances(
      final CamundaClient client, final String processId, final int count) {
    final List<Long> keys = new ArrayList<>();
    while (keys.size() < count) {
      final List<CamundaFuture<ProcessInstanceEvent>> wave = new ArrayList<>();
      for (int i = keys.size(); i < Math.min(keys.size() + CREATION_WAVE_SIZE, count); i++) {
        wave.add(client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send());
      }
      wave.forEach(future -> keys.add(future.join().getProcessInstanceKey()));
    }
    return keys;
  }

  public static CamundaFuture<ActivateJobsResponse> pollJobs(
      final CamundaClient client,
      final String jobType,
      final int maxJobs,
      final Duration requestTimeout) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(maxJobs)
        .timeout(JOB_TIMEOUT)
        .requestTimeout(requestTimeout)
        .send();
  }

  /**
   * A poll whose only job is to make the activation path check a job's secret references. While no
   * stream is registered a created job is announced to the workers unchecked, so a test that needs
   * the job parked has to poll for it first.
   */
  public static List<ActivatedJob> primingPoll(final CamundaClient client, final String jobType) {
    return pollJobs(client, jobType, 1, PRIMING_POLL_TIMEOUT).join().getJobs();
  }

  /** The one job of a response, so a poll that came back empty fails on the count. */
  public static ActivatedJob onlyJob(final ActivateJobsResponse response) {
    assertThat(response.getJobs()).hasSize(1);
    return response.getJobs().get(0);
  }

  /** Long polls for one job of the type and returns it. */
  public static ActivatedJob activateOneJob(final CamundaClient client, final String jobType) {
    return onlyJob(pollJobs(client, jobType, 1, LONG_POLL_TIMEOUT).join());
  }

  /**
   * Polls until every expected job was activated, and fails naming how many arrived if the await
   * budget runs out first.
   *
   * <p>Each poll is given what is left of that budget rather than the full long poll timeout, so a
   * last poll issued just before the deadline cannot keep the request open past it. Without that,
   * the method can overrun its own budget by a whole long poll, and the test it runs in reports the
   * overrun as a plain "wrong number of jobs".
   */
  public static Map<Long, ActivatedJob> pollUntilActivated(
      final CamundaClient client, final String jobType, final int expected) {
    final Map<Long, ActivatedJob> activated = new HashMap<>();
    final long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT.toMillis();
    long remaining;
    while (activated.size() < expected && (remaining = deadline - System.currentTimeMillis()) > 0) {
      final var requestTimeout =
          Duration.ofMillis(Math.min(remaining, LONG_POLL_TIMEOUT.toMillis()));
      pollJobs(client, jobType, expected, requestTimeout)
          .join()
          .getJobs()
          .forEach(job -> activated.put(job.getKey(), job));
    }
    assertThat(activated)
        .as(
            "every one of the %d jobs of type '%s' was activated within %s"
                .formatted(expected, jobType, AWAIT_TIMEOUT))
        .hasSize(expected);
    return activated;
  }

  /** Waits for the job of a process instance to be created and returns its key. */
  public static long awaitJobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  /**
   * Waits for the activation that handed this job to a worker to be exported. Both paths append a
   * {@code JOB_BATCH ACTIVATED} event naming the job, so this covers a long poll and a push alike.
   *
   * <p>Every assertion about what an activation wrote, or did not write, belongs behind this. The
   * activation response reaches the client as soon as its records are committed, while exporting
   * them follows, so an assertion made straight after the client call can read a record set that
   * does not hold the activation yet: a search for a leaked secret value would pass without having
   * seen the records that carry the job, and a search for a park that should not have happened
   * would pass whether it did or not.
   */
  public static void awaitActivationExported(final String jobType, final long jobKey) {
    assertThat(
            RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
                .withType(jobType)
                .valueFilter(batch -> batch.getJobKeys().contains(jobKey))
                .limit(1)
                .exists())
        .as("the activation of job %s was exported".formatted(jobKey))
        .isTrue();
  }

  /**
   * Whether the resolution of the secret was requested for this job, i.e. whether the job was
   * parked on it. Reads what is recorded now, so it belongs behind an await of the record that
   * would follow the request, such as {@link #awaitActivationExported}.
   */
  public static boolean resolutionWasRequestedFor(final String secretName, final long jobKey) {
    return resolutionRequestsFor(secretName).anyMatch(value -> value.getJobKeys().contains(jobKey));
  }

  /** Waits until the resolution of the secret is requested for any job. */
  public static void awaitResolutionRequested(final String secretName) {
    Awaitility.await("until the resolution of secret '%s' is requested".formatted(secretName))
        .atMost(AWAIT_TIMEOUT)
        .until(() -> resolutionRequestsFor(secretName).findAny().isPresent());
  }

  /**
   * The states the resolution of the secret failed with, as recorded now. Both a store that is gone
   * and a secret the store answers for and does not have end in the same {@code
   * SECRET_RESOLUTION_ERROR} incident, so this is what tells a test which of the two it got.
   */
  public static List<ResolutionState> resolutionFailureStates(final String secretName) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_FAILED)
        .filter(record -> isReferenceRecordFor(record, secretName))
        .map(record -> ((SecretReferenceRecordValue) record.getValue()).getResolutionState())
        .toList();
  }

  /** The jobs the resolution of the secret was requested for, i.e. the jobs it parked. */
  public static Set<Long> parkedJobKeys(final String secretName) {
    final Set<Long> parked = new LinkedHashSet<>();
    resolutionRequestsFor(secretName).forEach(request -> parked.addAll(request.getJobKeys()));
    return parked;
  }

  /**
   * The jobs each {@code BATCH_JOBS_REACTIVATED} of the secret carried, one list per round of the
   * reactivation chain, in the order the rounds were written.
   */
  public static List<List<Long>> reactivationRounds(final String secretName) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .filter(record -> isReferenceRecordFor(record, secretName))
        .map(record -> ((SecretReferenceRecordValue) record.getValue()).getJobKeys())
        .toList();
  }

  /** The partitions that requested a resolution of the secret. */
  public static List<Integer> resolutionRequestPartitions(final String secretName) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_REQUESTED)
        .filter(record -> isReferenceRecordFor(record, secretName))
        .map(record -> record.getPartitionId())
        .distinct()
        .toList();
  }

  /**
   * Asserts that nothing exported so far carries the value a worker was handed. Inspects what is
   * recorded now, so it belongs behind {@link #awaitActivationExported}.
   */
  public static void assertNoRecordCarriesValue(final String secretValue) {
    assertThat(RecordingExporter.getRecords())
        .as("no exported record carries the resolved secret value")
        .noneMatch(record -> record.toJson().contains(secretValue));
  }

  /**
   * The incidents raised for a process instance, as recorded now: a count of them is only ever
   * asserted to be complete, so this must not wait for one more.
   */
  public static List<IncidentRecordValue> incidentsOf(final long processInstanceKey) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == IncidentIntent.CREATED)
        .filter(record -> record.getValue() instanceof IncidentRecordValue)
        .map(record -> (IncidentRecordValue) record.getValue())
        .filter(incident -> incident.getProcessInstanceKey() == processInstanceKey)
        .toList();
  }

  /** Asserts that no incident was raised at all, as recorded now. */
  public static void assertNoIncidentWasRaised() {
    assertThat(
            RecordingExporter.getRecords().stream()
                .filter(record -> record.getIntent() == IncidentIntent.CREATED))
        .as("no incident was raised")
        .isEmpty();
  }

  public static void awaitStreamRegistered(
      final TestStandaloneBroker broker, final String jobType) {
    final var actuator = JobStreamActuator.of(broker);
    Awaitility.await("until a stream for job type '%s' is registered".formatted(jobType))
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }

  public static void awaitNoStreamRegistered(
      final TestStandaloneBroker broker, final String jobType) {
    final var actuator = JobStreamActuator.of(broker);
    Awaitility.await("until no stream for job type '%s' is registered".formatted(jobType))
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .doNotHaveJobType(jobType));
  }

  private static Stream<SecretReferenceRecordValue> resolutionRequestsFor(final String secretName) {
    return RecordingExporter.getRecords().stream()
        .filter(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_REQUESTED)
        .filter(record -> isReferenceRecordFor(record, secretName))
        .map(record -> (SecretReferenceRecordValue) record.getValue());
  }

  private static boolean isReferenceRecordFor(final Record<?> record, final String secretName) {
    return record.getValue() instanceof final SecretReferenceRecordValue value
        && secretName.equals(value.getSecretReference());
  }
}
