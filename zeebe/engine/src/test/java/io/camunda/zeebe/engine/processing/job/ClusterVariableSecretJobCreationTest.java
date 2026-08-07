/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the job-creation join described in issue #58932: a {@code camunda.vars.*} reference to a
 * {@code SECRET_REFERENCE} cluster variable folds that variable's secret references onto the
 * created job's {@code secretReferences}, merged with the element's direct {@code
 * camunda.secrets.*} references.
 */
public final class ClusterVariableSecretJobCreationTest {

  /**
   * All referenced secrets resolve to a cached value, otherwise activation would remove the jobs
   * from the batch before {@code getSecretReferences()} could be asserted on (see
   * SecretReferenceInputMappingTest for the same setup with direct secret references).
   */
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(SecretStoreRegistries.resolveAll("resolved"));

  private static final String TENANT = "tenant-1";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setup() {
    ENGINE.tenant().newTenant().withTenantId(TENANT).create();
  }

  @Test
  public void shouldFoldTenantScopedClusterVariableSecretOntoJob() {
    // given
    ENGINE
        .clusterVariables()
        .withName("creds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.token"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("cv-secret-job")
                        .zeebeInputExpression("camunda.vars.tenant.creds.token", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("cv-secret").withTenantId(TENANT).create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("cv-secret-job")
            .withTenantId(TENANT)
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(tuple(SecretStoreRegistry.DEFAULT_STORE_ID, "token", "/authToken"));
  }

  @Test
  public void shouldMergeDirectAndClusterVariableSecretReferences() {
    // given
    ENGINE
        .clusterVariables()
        .withName("creds2")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.cvToken"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-merge")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("cv-secret-merge-job")
                        .zeebeInputExpression("camunda.vars.tenant.creds2.token", "fromVar")
                        .zeebeInputExpression("camunda.secrets.directToken", "direct"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("cv-secret-merge").withTenantId(TENANT).create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("cv-secret-merge-job")
            .withTenantId(TENANT)
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactlyInAnyOrder(
            tuple(SecretStoreRegistry.DEFAULT_STORE_ID, "cvToken", "/fromVar"),
            tuple(SecretStoreRegistry.DEFAULT_STORE_ID, "directToken", "/direct"));
  }

  @Test
  public void shouldNoOpForMissingClusterVariable() {
    // given: no cluster variable named "missing" exists
    final var process =
        Bpmn.createExecutableProcess("cv-secret-missing")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("cv-secret-missing-job")
                        .zeebeInputExpression("camunda.vars.tenant.missing.token", "fromVar"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("cv-secret-missing").withTenantId(TENANT).create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("cv-secret-missing-job")
            .withTenantId(TENANT)
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldFoldGloballyScopedClusterVariableSecretOntoJobForClusterReference() {
    // given
    ENGINE
        .clusterVariables()
        .withName("globalCreds")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.globalToken"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-cluster")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("cv-secret-cluster-job")
                        .zeebeInputExpression(
                            "camunda.vars.cluster.globalCreds.token", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("cv-secret-cluster").withTenantId(TENANT).create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("cv-secret-cluster-job")
            .withTenantId(TENANT)
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(tuple(SecretStoreRegistry.DEFAULT_STORE_ID, "globalToken", "/authToken"));
  }

  @Test
  public void shouldFoldGloballyScopedClusterVariableSecretOntoJobForEnvReference() {
    // given
    ENGINE
        .clusterVariables()
        .withName("envCreds")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.envToken"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-env")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("cv-secret-env-job")
                        .zeebeInputExpression("camunda.vars.env.envCreds.token", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("cv-secret-env").withTenantId(TENANT).create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("cv-secret-env-job")
            .withTenantId(TENANT)
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(tuple(SecretStoreRegistry.DEFAULT_STORE_ID, "envToken", "/authToken"));
  }

  @Test
  public void shouldNotFoldTenantScopedClusterVariableSecretAcrossTenants() {
    // given
    final var otherTenant = "tenant-2";
    ENGINE.tenant().newTenant().withTenantId(otherTenant).create();

    ENGINE
        .clusterVariables()
        .withName("isolatedCreds")
        .setTenantScope()
        .withTenantId(TENANT)
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.isolatedToken"))
        .create();

    final var process =
        Bpmn.createExecutableProcess("cv-secret-tenant-isolation")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("cv-secret-tenant-isolation-job")
                        .zeebeInputExpression(
                            "camunda.vars.tenant.isolatedCreds.token", "authToken"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(otherTenant).deploy();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId("cv-secret-tenant-isolation")
        .withTenantId(otherTenant)
        .create();

    // then
    final JobRecordValue job =
        ENGINE
            .jobs()
            .withType("cv-secret-tenant-isolation-job")
            .withTenantId(otherTenant)
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getSecretReferences()).isEmpty();
  }
}
