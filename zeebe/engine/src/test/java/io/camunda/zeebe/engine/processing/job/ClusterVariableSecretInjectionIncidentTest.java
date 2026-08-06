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
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretActivationResponseCapture;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * End-to-end coverage for the race documented in the secret-injection-mismatch-incident design spec
 * (Addendum 1/2): a start execution listener creates a deterministic window between a service
 * task's input-mapping evaluation - which bakes the *then-current* cluster-variable placeholder
 * into the job's variables - and the job's actual creation, which resolves its secret reference
 * from whatever the cluster variable holds once the listener completes. If the cluster variable is
 * updated to point at a different secret inside that window, the job ends up with two disagreeing
 * pieces of state: a stale literal placeholder baked into its variables, and a fresh reference
 * resolved against the new value. {@code JobSecretInjector} then fails to find the stale
 * placeholder to replace and drops the job with a {@code SECRET_RESOLUTION_ERROR} incident (see
 * {@code JobBatchActivateProcessor#raiseIncidentJobSecretInjectionFailed}).
 *
 * <p>This proves three things no test previously covered: that the incident actually fires for this
 * specific cause, that its message says plainly resolving it will not fix anything - the job's data
 * is immutable once created, so re-resolving the same frozen record hits the identical mismatch
 * again - and that the one documented recovery path, process instance modification, actually works
 * end-to-end: the stale job is canceled, its incident auto-resolves, and the *fresh* job created
 * against now-stable cluster-variable state resolves and injects correctly.
 *
 * <p>The cluster variable is still tenant-scoped (the {@code camunda.vars.tenant.*} reference
 * requires that), but scoped to the {@code <default>} tenant rather than a custom one: completing a
 * job belonging to a non-default tenant needs either a full multi-tenancy-plus-authorization setup
 * (real user, tenant membership, granted permissions - see {@code MultiTenantJobOperationsTest}) or
 * an anonymous-auth escape hatch that {@code JobClient} does not expose, neither of which the race
 * under test needs to demonstrate. The default tenant sidesteps that entirely while exercising the
 * exact same {@code ClusterVariableJobSecretResolver} / {@code JobSecretInjector} code paths.
 */
public final class ClusterVariableSecretInjectionIncidentTest {

  private static final String JOB_TYPE = "cv-secret-injection-incident-job";
  private static final String START_EL_TYPE = "cv-secret-injection-incident-start-el";
  private static final String BPMN_PROCESS_ID = "cv-secret-injection-incident";
  private static final String ELEMENT_ID = "task";
  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  @Rule public final EngineRule engine;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SecretActivationResponseCapture secretActivation =
      new SecretActivationResponseCapture();

  public ClusterVariableSecretInjectionIncidentTest() {
    engine =
        EngineRule.singlePartition()
            .withSecretStoreRegistry(
                new SecretStoreRegistry(
                    Map.of("default", new NoopSecretStore()), Map.of("default", secretActivation)));
  }

  @Before
  public void setUp() {
    // the default tenant is not seeded automatically in this rule - it must exist as a real
    // tenant record before a tenant-scoped cluster variable can reference it, and before a job
    // belonging to it can be looked up by JobCommandPreconditionValidator's tenant-filtered lookup
    engine.tenant().newTenant().withTenantId(TENANT).create();
    secretActivation.install(engine.getCommandResponseWriter());
  }

  @Test
  public void shouldRaiseSecretResolutionIncidentAndRecoverViaProcessInstanceModification() {
    // given - a tenant-scoped SECRET_REFERENCE cluster variable pointing at "tokenA"
    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.tokenA"))
        .create();

    final var process =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeInputExpression("camunda.vars.tenant.creds.token", "authToken"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when - the instance is created: onActivate evaluates the input mapping against the current
    // (A) value and bakes "camunda.secrets.tokenA" into the element's job variables, then the
    // start-listener job is created and the engine pauses - a deterministic window opens here
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(BPMN_PROCESS_ID).create();
    final long staleListenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(START_EL_TYPE)
            .getFirst()
            .getKey();

    // and - while the listener job is pending, the cluster variable is updated to point at
    // "tokenB" (kind is immutable after creation, so the update omits it; setTenantScope is
    // required to address the same tenant-scoped variable)
    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withValue(Map.of("token", "camunda.secrets.tokenB"))
        .update();

    // and - completing the listener lets finalizeActivation run and resolve the reference from
    // *current* (now B) state, while the job's variables still literally hold "tokenA"
    engine.job().withKey(staleListenerJobKey).complete();
    final long staleJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();

    // and - only the new reference is cached, so checkSecrets finds it and injectSecretValues
    // actually attempts (and fails) the replacement instead of skipping the job as non-cached
    secretActivation.putSecret("tokenB", "resolved-B");

    // when - activating with request metadata present, so JobBatchActivateProcessor attempts
    // secret injection at all (responseValueFor only does so when hasRequestMetadata() is true)
    engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the mismatch between the job's baked-in placeholder and its resolved reference makes
    // injection fail, and the incident says plainly that resolving it will not fix anything
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withJobKey(staleJobKey)
            .getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.SECRET_RESOLUTION_ERROR);
    assertThat(incident.getValue().getErrorMessage())
        .contains("Resolving this incident will not fix it");

    // and - the job is excluded from activation: a later poll does not hand it out either
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).isEmpty();

    // when - recovering via the documented path: process instance modification terminates the
    // stuck element and reactivates it, creating a fresh job against now-stable state
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElements(ELEMENT_ID)
        .activateElement(ELEMENT_ID)
        .modify();

    // then - the stale job is canceled and its incident auto-resolved, both from the same
    // cancelJob call BpmnJobBehavior performs when the element is terminated
    assertThat(RecordingExporter.jobRecords(JobIntent.CANCELED).withRecordKey(staleJobKey).exists())
        .isTrue();
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withJobKey(staleJobKey)
                .exists())
        .isTrue();

    // and - the reactivated element goes through onActivate and the start listener again, exactly
    // like the original activation did; completing this fresh listener job (not the stale one,
    // which stays completed) is what lets finalizeActivation create the fresh task job below
    final long freshListenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(START_EL_TYPE)
            .limit(2)
            .asList()
            .stream()
            .map(Record::getKey)
            .filter(key -> key != staleListenerJobKey)
            .findFirst()
            .orElseThrow();
    engine.job().withKey(freshListenerJobKey).complete();

    // and - the reactivated element creates a fresh job: same type, same instance, but a
    // different key than the stale one - filtered explicitly rather than assuming stream order
    final long freshJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .limit(2)
            .asList()
            .stream()
            .map(Record::getKey)
            .filter(key -> key != staleJobKey)
            .findFirst()
            .orElseThrow();
    assertThat(freshJobKey).isNotEqualTo(staleJobKey);

    // when - activating the fresh job the same way as before
    engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(2L).activate();

    // then - it resolves and injects correctly now that the cluster-variable state is stable; the
    // captured field already holds the earlier (incident) response, so this polls on content
    // rather than just non-null - mirrors JobSecretActivationInjectionTest's pattern for a
    // repeated activation reusing the same captured field
    Awaitility.await("until the fresh job's activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(secretActivation.getActivationResponse().getJobs().get(0).getVariables())
                    .containsEntry("authToken", "resolved-B"));
  }

  @Test
  public void shouldReactivateJobAfterResolvingIncidentOnceVariableIsCorrected() {
    // given - same race as above: the job's variables literally hold "camunda.secrets.tokenA"
    // but its stored secret reference resolved against the updated cluster variable, tokenB
    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.tokenA"))
        .create();

    final var process =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent()
            .serviceTask(
                ELEMENT_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeStartExecutionListener(START_EL_TYPE)
                        .zeebeInputExpression("camunda.vars.tenant.creds.token", "authToken"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(BPMN_PROCESS_ID).create();
    final long listenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(START_EL_TYPE)
            .getFirst()
            .getKey();

    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withValue(Map.of("token", "camunda.secrets.tokenB"))
        .update();

    engine.job().withKey(listenerJobKey).complete();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();

    secretActivation.putSecret("tokenB", "resolved-B");

    // when - activation raises the mismatch incident
    engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();

    // and - the mismatched variable is corrected directly (whatever out-of-band fix an operator
    // applies) so it once again contains the placeholder the job's secret reference expects, then
    // the incident is resolved
    engine
        .variables()
        .ofScope(incident.getValue().getVariableScopeKey())
        .withDocument(Map.of("authToken", "camunda.secrets.tokenB"))
        .withLocalSemantic()
        .update();
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then - the job is activatable again and injects correctly this time
    final Record<JobBatchRecordValue> retried =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(3).withRequestId(3L).activate();
    assertThat(retried.getValue().getJobs()).hasSize(1);
    Awaitility.await("until the retried job's activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(secretActivation.getActivationResponse().getJobs().get(0).getVariables())
                    .containsEntry("authToken", "resolved-B"));
  }
}
