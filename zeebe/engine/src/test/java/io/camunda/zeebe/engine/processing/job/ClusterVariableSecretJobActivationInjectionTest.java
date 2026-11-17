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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * End-to-end coverage for issue #58932: a job worker's input mapping references a {@code
 * SECRET_REFERENCE} cluster variable, whose secret is injected into the activation response the
 * same way a direct {@code camunda.secrets.*} reference already is. Also covers, per review
 * feedback: a cache-lookup failure for a cluster-variable-derived secret (parity with the existing
 * rejection behavior for direct secrets), the same secret reached through both a direct and a
 * cluster-variable reference at two different target paths, and two different secrets (one direct,
 * one cluster-variable-derived) folded onto the same target path via string concatenation.
 */
public final class ClusterVariableSecretJobActivationInjectionTest {

  private static final String JOB_TYPE = "cv-secret-activation-job";
  private static final String TENANT = "tenant-1";

  @Rule public final EngineRule engine;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SecretActivationResponseCapture secretActivation =
      new SecretActivationResponseCapture();

  public ClusterVariableSecretJobActivationInjectionTest() {
    engine =
        EngineRule.singlePartition()
            .withSecretStoreRegistry(
                new SecretStoreRegistry(
                    Map.of("default", new NoopSecretStore()), Map.of("default", secretActivation)));
  }

  @Before
  public void setUp() {
    engine.tenant().newTenant().withTenantId(TENANT).create();
    secretActivation.install(engine.getCommandResponseWriter());
  }

  private long createInstanceAndAwaitJob(final String bpmnProcessId, final String jobType) {
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(bpmnProcessId).withTenantId(TENANT).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .getFirst();
    return processInstanceKey;
  }

  @Test
  public void shouldInjectTenantScopedClusterVariableSecretOnActivation() {
    // given
    secretActivation.putSecret("token", "resolved-secret");
    engine
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.token"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-activation")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression("camunda.vars.tenant.creds.token", "authToken"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    createInstanceAndAwaitJob("cv-secret-activation", JOB_TYPE);

    // when
    final Record<JobBatchRecordValue> activated =
        engine
            .jobs()
            .withType(JOB_TYPE)
            .withTenantId(TENANT)
            .withRequestStreamId(1)
            .withRequestId(1L)
            .activate();

    // then - the persisted ACTIVATED event keeps the unresolved placeholder (no secret in the log)
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authToken", "camunda.secrets.token");

    // and - the worker response carries the resolved secret value
    assertThat(secretActivation.awaitActivationResponse().getJobs().get(0).getVariables())
        .containsEntry("authToken", "resolved-secret");
  }

  @Test
  public void shouldInjectSecretReachedThroughFieldPathAccess() {
    // given: the variable's value nests the secret one level deeper than the whole-variable case
    secretActivation.putSecret("nestedToken", "resolved-nested-secret");
    engine
        .clusterVariables()
        .withName("nestedCreds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("auth", Map.of("token", "camunda.secrets.nestedToken")))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-field-access")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE + "-field")
                        .zeebeInputExpression(
                            "camunda.vars.tenant.nestedCreds.auth.token", "authToken"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    createInstanceAndAwaitJob("cv-secret-field-access", JOB_TYPE + "-field");

    // when
    engine
        .jobs()
        .withType(JOB_TYPE + "-field")
        .withTenantId(TENANT)
        .withRequestStreamId(1)
        .withRequestId(1L)
        .activate();

    // then
    assertThat(secretActivation.awaitActivationResponse().getJobs().get(0).getVariables())
        .containsEntry("authToken", "resolved-nested-secret");
  }

  @Test
  public void shouldRejectActivationWhenClusterVariableSecretLookupThrows() {
    // given - a cache failure for a cluster-variable-derived secret must behave exactly like a
    // cache failure for a direct one: by the time activation runs, JobSecretInjector reads the same
    // JobRecord.secretReferences list regardless of where each entry came from
    secretActivation.failResolution(true);
    engine
        .clusterVariables()
        .withName("brokenCreds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.brokenToken"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-lookup-failure")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE + "-broken")
                        .zeebeInputExpression("camunda.vars.tenant.brokenCreds.token", "authToken"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    createInstanceAndAwaitJob("cv-secret-lookup-failure", JOB_TYPE + "-broken");

    // when - the cache failure propagates and fails the activation command, exactly as it does in
    // JobSecretActivationInjectionTest#shouldFailActivationWhenSecretCacheLookupThrows
    final Record<JobBatchRecordValue> rejection =
        engine
            .jobs()
            .withType(JOB_TYPE + "-broken")
            .withTenantId(TENANT)
            .expectRejection()
            .activate();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.PROCESSING_ERROR);
    assertThat(rejection.getRejectionReason()).contains("resolver exploded");
  }

  @Test
  public void shouldResolveSameSecretReachedThroughDirectAndClusterVariableReference() {
    // given - the same secret is reached both directly and through a cluster variable, at two
    // different target paths
    secretActivation.putSecret("sharedToken", "resolved-shared-secret");
    engine
        .clusterVariables()
        .withName("sharedCreds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.sharedToken"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-shared")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE + "-shared")
                        .zeebeInputExpression("camunda.secrets.sharedToken", "direct")
                        .zeebeInputExpression("camunda.vars.tenant.sharedCreds.token", "fromVar"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    createInstanceAndAwaitJob("cv-secret-shared", JOB_TYPE + "-shared");

    // when
    engine
        .jobs()
        .withType(JOB_TYPE + "-shared")
        .withTenantId(TENANT)
        .withRequestStreamId(1)
        .withRequestId(1L)
        .activate();

    // then - both paths resolve to the same value; the duplicate (storeId, name) pair reached
    // through two different paths causes no error (BpmnJobBehavior's merge only dedups within the
    // same path — see mergedSecretReferences in Task 2 — and here the paths genuinely differ)
    assertThat(secretActivation.awaitActivationResponse().getJobs().get(0).getVariables())
        .containsEntry("direct", "resolved-shared-secret")
        .containsEntry("fromVar", "resolved-shared-secret");
  }

  @Test
  public void shouldResolveTwoDifferentSecretsFoldedAtTheSameTargetPath() {
    // given - a direct secret and a cluster-variable-derived secret are concatenated into one
    // target, so both fold onto the exact same "/combined" leaf pointer
    secretActivation.putSecret("directPart", "AAA");
    secretActivation.putSecret("varPart", "BBB");
    engine
        .clusterVariables()
        .withName("concatCreds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.varPart"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-concat")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(JOB_TYPE + "-concat")
                        .zeebeInputExpression(
                            "camunda.secrets.directPart + camunda.vars.tenant.concatCreds.token",
                            "combined"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    createInstanceAndAwaitJob("cv-secret-concat", JOB_TYPE + "-concat");

    // when
    engine
        .jobs()
        .withType(JOB_TYPE + "-concat")
        .withTenantId(TENANT)
        .withRequestStreamId(1)
        .withRequestId(1L)
        .activate();

    // then - JobSecretInjector already replaces N independent placeholders within one leaf's string
    // value (the same mechanism a direct multi-secret concatenation already exercises, e.g.
    // SecretReferenceInputMappingTest#shouldResolveSecretReferenceInsideConcatenationInInputMapping);
    // two entries at the same path — one direct, one cluster-variable-derived — is not a new code
    // path, just two elements in the same pre-existing loop
    assertThat(secretActivation.awaitActivationResponse().getJobs().get(0).getVariables())
        .containsEntry("combined", "AAABBB");
  }
}
