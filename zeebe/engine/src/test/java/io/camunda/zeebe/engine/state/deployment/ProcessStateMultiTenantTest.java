/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProcessStateMultiTenantTest {

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";
  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableProcessState processState;
  private MutableProcessingState processingState;
  private KeyGenerator keyGenerator;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    processState = processingState.getProcessState();
    keyGenerator = processingState.getKeyGenerator();
  }

  @Test
  public void shouldPutDeploymentForDifferentTenants() {
    // given
    final long processKey = keyGenerator.nextKey();
    final String processId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Deployment = createDeploymentRecord(TENANT_1, processKey, processId, version);
    final var tenant2Deployment = createDeploymentRecord(TENANT_2, processKey, processId, version);

    // when
    processState.putDeployment(tenant1Deployment);
    processState.putDeployment(tenant2Deployment);

    // then
    var tenant1DeployedProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    var tenant2DeployedProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);

    tenant1DeployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString(processId), version, TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    tenant2DeployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString(processId), version, TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);

    tenant1DeployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString(processId), TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    tenant2DeployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString(processId), TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);
  }

  @Test
  public void shouldPutProcessForMultipleTenants() {
    // given
    final long processKey = keyGenerator.nextKey();
    final String processId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Process = createProcessRecord(TENANT_1, processKey, processId, version);
    final var tenant2Process = createProcessRecord(TENANT_2, processKey, processId, version);

    // when
    processState.putProcess(processKey, tenant1Process);
    processState.putProcess(processKey, tenant2Process);

    // then
    var tenant1DeployedProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    var tenant2DeployedProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);

    tenant1DeployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString(processId), version, TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    tenant2DeployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString(processId), version, TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);

    tenant1DeployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString(processId), TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    tenant2DeployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString(processId), TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);

    assertThat(tenant1Process.getChecksumBuffer()).isNotEqualTo(tenant2Process.getChecksumBuffer());
    assertThat(processState.getLatestVersionDigest(wrapString(processId), TENANT_1))
        .isEqualTo(tenant1Process.getChecksumBuffer());
    assertThat(processState.getLatestVersionDigest(wrapString(processId), TENANT_2))
        .isEqualTo(tenant2Process.getChecksumBuffer());

    assertThat(processState.getLatestProcessVersion(processId, TENANT_1)).isEqualTo(version);
    assertThat(processState.getLatestProcessVersion(processId, TENANT_2)).isEqualTo(version);

    assertThat(processState.getNextProcessVersion(processId, TENANT_1)).isEqualTo(version + 1);
    assertThat(processState.getNextProcessVersion(processId, TENANT_2)).isEqualTo(version + 1);
  }

  @Test
  public void shouldStoreProcessDefinitionKeyByProcessIdAndDeploymentKeyForMultipleTenants() {
    // given
    final var processKey = keyGenerator.nextKey();
    final var deploymentKey = keyGenerator.nextKey();
    final var processId = Strings.newRandomValidBpmnId();
    final var version = 1;
    final var tenant1Process =
        createProcessRecord(TENANT_1, processKey, processId, version)
            .setDeploymentKey(deploymentKey);
    final var tenant2Process =
        createProcessRecord(TENANT_2, processKey, processId, version)
            .setDeploymentKey(deploymentKey);
    processState.putProcess(processKey, tenant1Process);
    processState.putProcess(processKey, tenant2Process);

    // when
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(tenant1Process);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(tenant2Process);

    // then
    final var tenant1DeployedProcess =
        processState.getProcessByProcessIdAndDeploymentKey(
            wrapString(processId), deploymentKey, TENANT_1);
    assertDeployedProcess(tenant1DeployedProcess, TENANT_1, processKey, processId, version);
    final var tenant2DeployedProcess =
        processState.getProcessByProcessIdAndDeploymentKey(
            wrapString(processId), deploymentKey, TENANT_2);
    assertDeployedProcess(tenant2DeployedProcess, TENANT_2, processKey, processId, version);
  }

  @Test
  public void shouldUpdateProcessStateForTenant() {
    // given
    final long processKey = keyGenerator.nextKey();
    final String processId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Process = createProcessRecord(TENANT_1, processKey, processId, version);
    final var tenant2Process = createProcessRecord(TENANT_2, processKey, processId, version);
    processState.putProcess(processKey, tenant1Process);
    processState.putProcess(processKey, tenant2Process);
    final var tenant1InitialProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_1);
    final var tenant2InitialProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_2);

    // when
    processState.updateProcessState(tenant1Process, PersistedProcessState.PENDING_DELETION);

    // then
    assertThat(tenant1InitialProcess.getState())
        .describedAs("Tenant 1 started with ACTIVE state")
        .isEqualTo(PersistedProcessState.ACTIVE);
    assertThat(tenant2InitialProcess.getState())
        .describedAs("Tenant 2 started with ACTIVE state")
        .isEqualTo(PersistedProcessState.ACTIVE);
    final var tenant1UpdatedProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_1);
    assertThat(tenant1UpdatedProcess.getState())
        .describedAs("Tenant 1 state is updated")
        .isEqualTo(PersistedProcessState.PENDING_DELETION);
    final var tenant2UpdatedProcess = processState.getProcessByKeyAndTenant(processKey, TENANT_2);
    assertThat(tenant2UpdatedProcess.getState())
        .describedAs("Tenant 2 state is unchanged")
        .isEqualTo(PersistedProcessState.ACTIVE);
  }

  @Test
  public void shouldDeleteProcessForTenant() {
    final long processKey = keyGenerator.nextKey();
    final String processId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Process = createProcessRecord(TENANT_1, processKey, processId, version);
    final var tenant2Process = createProcessRecord(TENANT_2, processKey, processId, version);
    processState.putProcess(processKey, tenant1Process);
    processState.putProcess(processKey, tenant2Process);

    // when
    processState.deleteProcess(tenant1Process);

    // then
    assertThat(processState.getProcessByKeyAndTenant(processKey, TENANT_1))
        .describedAs("Tenant 1 is removed from the state")
        .isNull();
    assertThat(processState.getProcessByKeyAndTenant(processKey, TENANT_2))
        .describedAs("Tenant 2 is not removed from the state")
        .isNotNull();
  }

  private DeploymentRecord createDeploymentRecord(
      final String tenantId, final long processKey, final String processId, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "test",
                task -> {
                  task.zeebeJobType("type");
                })
            .endEvent()
            .done();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final String resourceName = "process.bpmn";
    final var resource = wrapString(Bpmn.convertToString(modelInstance));
    final var checksum = wrapString("checksum" + tenantId);
    deploymentRecord
        .setTenantId(tenantId)
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(resource);

    deploymentRecord
        .processesMetadata()
        .add()
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(processKey)
        .setResourceName(resourceName)
        .setChecksum(checksum)
        .setTenantId(tenantId);

    return deploymentRecord;
  }

  public static ProcessRecord createProcessRecord(
      final String tenantId, final long processKey, final String processId, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("test", task -> task.zeebeJobType("type"))
            .endEvent()
            .done();

    final ProcessRecord processRecord = new ProcessRecord();
    final String resourceName = "process.bpmn";
    final var resource = wrapString(Bpmn.convertToString(modelInstance));
    final var checksum = wrapString("checksum" + tenantId);

    processRecord
        .setResourceName(wrapString(resourceName))
        .setResource(resource)
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(processKey)
        .setResourceName(resourceName)
        .setChecksum(checksum)
        .setTenantId(tenantId);

    return processRecord;
  }

  private void assertDeployedProcess(
      final DeployedProcess deployedProcess,
      final String expectedTenant,
      final long expectedProcessKey,
      final String expectedProcessId,
      final int expectedVersion) {
    assertThat(deployedProcess)
        .extracting(
            DeployedProcess::getTenantId,
            DeployedProcess::getKey,
            DeployedProcess::getBpmnProcessId,
            DeployedProcess::getVersion)
        .describedAs("Gets correct process by key and tenant")
        .containsExactly(
            expectedTenant, expectedProcessKey, wrapString(expectedProcessId), expectedVersion);
  }
}
