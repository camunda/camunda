/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.deployment;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.mutable.MutableProcessState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessStateTest {

  private static final Long FIRST_PROCESS_KEY =
      Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1);
  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableProcessState processState;
  private MutableZeebeState zeebeState;

  @Before
  public void setUp() {
    zeebeState = stateRule.getZeebeState();
    processState = zeebeState.getProcessState();
  }

  @Test
  public void shouldGetInitialProcessVersion() {
    // given

    // when
    final long nextProcessVersion = processState.getProcessVersion("foo");

    // then
    assertThat(nextProcessVersion).isZero();
  }

  @Test
  public void shouldGetProcessVersion() {
    // given
    final var processRecord = creatingProcessRecord(zeebeState);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    final long processVersion = processState.getProcessVersion("processId");

    // then
    assertThat(processVersion).isEqualTo(1L);
  }

  @Test
  public void shouldIncrementProcessVersion() {
    // given
    final var processRecord = creatingProcessRecord(zeebeState);
    processState.putProcess(processRecord.getKey(), processRecord);

    final var processRecord2 = creatingProcessRecord(zeebeState);
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // when
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // then
    final long processVersion = processState.getProcessVersion("processId");
    assertThat(processVersion).isEqualTo(2L);
  }

  @Test
  public void shouldNotIncrementProcessVersionForDifferentProcessId() {
    // given
    final var processRecord = creatingProcessRecord(zeebeState);
    processState.putProcess(processRecord.getKey(), processRecord);
    final var processRecord2 = creatingProcessRecord(zeebeState, "other");

    // when
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // then
    final long processVersion = processState.getProcessVersion("processId");
    assertThat(processVersion).isEqualTo(1L);
    final long otherversion = processState.getProcessVersion("other");
    assertThat(otherversion).isEqualTo(1L);
  }

  @Test
  public void shouldReturnNullOnGetLatest() {
    // given

    // when
    final DeployedProcess deployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("deployedProcess"));

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnNullOnGetProcessByKey() {
    // given

    // when
    final DeployedProcess deployedProcess = processState.getProcessByKey(0);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnNullOnGetProcessByProcessIdAndVersion() {
    // given

    // when
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("foo"), 0);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnEmptyListOnGetProcesses() {
    // given

    // when
    final Collection<DeployedProcess> deployedProcess = processState.getProcesses();

    // then
    Assertions.assertThat(deployedProcess).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListOnGetProcessesByProcessId() {
    // given

    // when
    final Collection<DeployedProcess> deployedProcess =
        processState.getProcessesByBpmnProcessId(wrapString("foo"));

    // then
    Assertions.assertThat(deployedProcess).isEmpty();
  }

  @Test
  public void shouldPutDeploymentToState() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);

    // when
    processState.putDeployment(deploymentRecord);

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);

    Assertions.assertThat(deployedProcess).isNotNull();
  }

  @Test
  public void shouldPutProcessToState() {
    // given
    final var processRecord = creatingProcessRecord(zeebeState);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);

    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getKey()).isEqualTo(processRecord.getKey());
    assertThat(deployedProcess.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());

    final var processByKey = processState.getProcessByKey(processRecord.getKey());
    assertThat(processByKey).isNotNull();
    assertThat(processByKey.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(processByKey.getVersion()).isEqualTo(1);
    assertThat(processByKey.getKey()).isEqualTo(processRecord.getKey());
    assertThat(processByKey.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(processByKey.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
  }

  @Test
  public void shouldUpdateLatestDigestOnPutProcessToState() {
    // given
    final var processRecord = creatingProcessRecord(zeebeState);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final var checksum = processState.getLatestVersionDigest(wrapString("processId"));
    assertThat(checksum).isEqualTo(processRecord.getChecksumBuffer());
  }

  @Test
  public void shouldUpdateLatestProcessOnPutProcessToState() {
    // given
    final var processRecord = creatingProcessRecord(zeebeState);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final DeployedProcess deployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"));

    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getKey()).isEqualTo(processRecord.getKey());
    assertThat(deployedProcess.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);

    // when
    processState.putDeployment(deploymentRecord);
    deploymentRecord.processesMetadata().iterator().next().setKey(212).setBpmnProcessId("other");

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);

    Assertions.assertThat(deployedProcess.getKey())
        .isNotEqualTo(deploymentRecord.processesMetadata().iterator().next().getKey());
    assertThat(deploymentRecord.processesMetadata().iterator().next().getBpmnProcessIdBuffer())
        .isEqualTo(BufferUtil.wrapString("other"));
    Assertions.assertThat(deployedProcess.getBpmnProcessId())
        .isEqualTo(BufferUtil.wrapString("processId"));
  }

  @Test
  public void shouldStoreDifferentProcessVersionsOnPutDeployments() {
    // given

    // when
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    processState.putDeployment(creatingDeploymentRecord(zeebeState));

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);

    final DeployedProcess secondProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 2);

    Assertions.assertThat(deployedProcess).isNotNull();
    Assertions.assertThat(secondProcess).isNotNull();

    Assertions.assertThat(deployedProcess.getBpmnProcessId())
        .isEqualTo(secondProcess.getBpmnProcessId());
    Assertions.assertThat(deployedProcess.getResourceName())
        .isEqualTo(secondProcess.getResourceName());
    Assertions.assertThat(deployedProcess.getKey()).isNotEqualTo(secondProcess.getKey());

    Assertions.assertThat(deployedProcess.getVersion()).isEqualTo(1);
    Assertions.assertThat(secondProcess.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldRestartVersionCountOnDifferentProcessId() {
    // given
    processState.putDeployment(creatingDeploymentRecord(zeebeState));

    // when
    processState.putDeployment(creatingDeploymentRecord(zeebeState, "otherId"));

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);

    final DeployedProcess secondProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("otherId"), 1);

    Assertions.assertThat(deployedProcess).isNotNull();
    Assertions.assertThat(secondProcess).isNotNull();

    // getKey's should increase
    Assertions.assertThat(deployedProcess.getKey()).isEqualTo(FIRST_PROCESS_KEY);
    Assertions.assertThat(secondProcess.getKey()).isEqualTo(FIRST_PROCESS_KEY + 1);

    // but versions should restart
    Assertions.assertThat(deployedProcess.getVersion()).isEqualTo(1);
    Assertions.assertThat(secondProcess.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldGetLatestDeployedProcess() {
    // given
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    processState.putDeployment(creatingDeploymentRecord(zeebeState));

    // when
    final DeployedProcess latestProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"));

    // then
    final DeployedProcess firstProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);
    final DeployedProcess secondProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 2);

    Assertions.assertThat(latestProcess).isNotNull();
    Assertions.assertThat(firstProcess).isNotNull();
    Assertions.assertThat(secondProcess).isNotNull();

    Assertions.assertThat(latestProcess.getBpmnProcessId())
        .isEqualTo(secondProcess.getBpmnProcessId());

    Assertions.assertThat(firstProcess.getKey()).isNotEqualTo(latestProcess.getKey());
    Assertions.assertThat(latestProcess.getKey()).isEqualTo(secondProcess.getKey());

    Assertions.assertThat(latestProcess.getResourceName())
        .isEqualTo(secondProcess.getResourceName());
    Assertions.assertThat(latestProcess.getResource()).isEqualTo(secondProcess.getResource());

    Assertions.assertThat(firstProcess.getVersion()).isEqualTo(1);
    Assertions.assertThat(latestProcess.getVersion()).isEqualTo(2);
    Assertions.assertThat(secondProcess.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldGetLatestDeployedProcessAfterDeploymentWasAdded() {
    // given
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    final DeployedProcess firstLatest =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"));

    // when
    processState.putDeployment(creatingDeploymentRecord(zeebeState));

    // then
    final DeployedProcess latestProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"));

    Assertions.assertThat(firstLatest).isNotNull();
    Assertions.assertThat(latestProcess).isNotNull();

    Assertions.assertThat(firstLatest.getBpmnProcessId())
        .isEqualTo(latestProcess.getBpmnProcessId());

    Assertions.assertThat(latestProcess.getKey()).isNotEqualTo(firstLatest.getKey());

    Assertions.assertThat(firstLatest.getResourceName()).isEqualTo(latestProcess.getResourceName());

    Assertions.assertThat(latestProcess.getVersion()).isEqualTo(2);
    Assertions.assertThat(firstLatest.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldGetExecutableProcess() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);
    processState.putDeployment(deploymentRecord);

    // when
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1);

    // then
    final ExecutableProcess process = deployedProcess.getProcess();
    Assertions.assertThat(process).isNotNull();
    final AbstractFlowElement serviceTask = process.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetExecutableProcessByKey() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);
    processState.putDeployment(deploymentRecord);

    // when
    final long processDefinitionKey = FIRST_PROCESS_KEY;
    final DeployedProcess deployedProcess = processState.getProcessByKey(processDefinitionKey);

    // then
    final ExecutableProcess process = deployedProcess.getProcess();
    Assertions.assertThat(process).isNotNull();
    final AbstractFlowElement serviceTask = process.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetExecutableProcessByLatestProcess() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(zeebeState);
    processState.putDeployment(deploymentRecord);

    // when
    final DeployedProcess deployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"));

    // then
    final ExecutableProcess process = deployedProcess.getProcess();
    Assertions.assertThat(process).isNotNull();
    final AbstractFlowElement serviceTask = process.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetAllProcesses() {
    // given
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    processState.putDeployment(creatingDeploymentRecord(zeebeState, "otherId"));

    // when
    final Collection<DeployedProcess> processes = processState.getProcesses();

    // then
    assertThat(processes.size()).isEqualTo(3);
    Assertions.assertThat(processes)
        .extracting(DeployedProcess::getBpmnProcessId)
        .contains(wrapString("processId"), wrapString("otherId"));
    Assertions.assertThat(processes).extracting(DeployedProcess::getVersion).contains(1, 2, 1);

    Assertions.assertThat(processes)
        .extracting(DeployedProcess::getKey)
        .containsOnly(FIRST_PROCESS_KEY, FIRST_PROCESS_KEY + 1, FIRST_PROCESS_KEY + 2);
  }

  @Test
  public void shouldGetAllProcessesWithProcessId() {
    // given
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    processState.putDeployment(creatingDeploymentRecord(zeebeState));

    // when
    final Collection<DeployedProcess> processes =
        processState.getProcessesByBpmnProcessId(wrapString("processId"));

    // then
    Assertions.assertThat(processes)
        .extracting(DeployedProcess::getBpmnProcessId)
        .containsOnly(wrapString("processId"));
    Assertions.assertThat(processes).extracting(DeployedProcess::getVersion).containsOnly(1, 2);

    Assertions.assertThat(processes)
        .extracting(DeployedProcess::getKey)
        .containsOnly(FIRST_PROCESS_KEY, FIRST_PROCESS_KEY + 1);
  }

  @Test
  public void shouldNotGetProcessesWithOtherProcessId() {
    // given
    processState.putDeployment(creatingDeploymentRecord(zeebeState));
    processState.putDeployment(creatingDeploymentRecord(zeebeState, "otherId"));

    // when
    final Collection<DeployedProcess> processes =
        processState.getProcessesByBpmnProcessId(wrapString("otherId"));

    // then
    assertThat(processes.size()).isEqualTo(1);
    Assertions.assertThat(processes)
        .extracting(DeployedProcess::getBpmnProcessId)
        .containsOnly(wrapString("otherId"));
    Assertions.assertThat(processes).extracting(DeployedProcess::getVersion).containsOnly(1);

    final long expectedProcessDefinitionKey =
        Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 2);
    Assertions.assertThat(processes)
        .extracting(DeployedProcess::getKey)
        .containsOnly(expectedProcessDefinitionKey);
  }

  @Test
  public void shouldReturnHighestVersionInsteadOfMostRecent() {
    // given
    final String processId = "process";
    processState.putDeployment(creatingDeploymentRecord(zeebeState, processId, 2));
    processState.putDeployment(creatingDeploymentRecord(zeebeState, processId, 1));

    // when
    final DeployedProcess latestProcess =
        processState.getLatestProcessVersionByProcessId(wrapString(processId));

    // then
    Assertions.assertThat(latestProcess.getVersion()).isEqualTo(2);
  }

  public static DeploymentRecord creatingDeploymentRecord(final MutableZeebeState zeebeState) {
    return creatingDeploymentRecord(zeebeState, "processId");
  }

  public static DeploymentRecord creatingDeploymentRecord(
      final MutableZeebeState zeebeState, final String processId) {
    final MutableProcessState processState = zeebeState.getProcessState();
    final int version = processState.getProcessVersion(processId) + 1;
    return creatingDeploymentRecord(zeebeState, processId, version);
  }

  public static DeploymentRecord creatingDeploymentRecord(
      final MutableZeebeState zeebeState, final String processId, final int version) {
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
    final var checksum = wrapString("checksum");
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(resource);

    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();
    final long key = keyGenerator.nextKey();

    deploymentRecord
        .processesMetadata()
        .add()
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(key)
        .setResourceName(resourceName)
        .setChecksum(checksum);

    return deploymentRecord;
  }

  public static ProcessRecord creatingProcessRecord(final MutableZeebeState zeebeState) {
    return creatingProcessRecord(zeebeState, "processId");
  }

  public static ProcessRecord creatingProcessRecord(
      final MutableZeebeState zeebeState, final String processId) {
    final MutableProcessState processState = zeebeState.getProcessState();
    final int version = processState.getProcessVersion(processId) + 1;
    return creatingProcessRecord(zeebeState, processId, version);
  }

  public static ProcessRecord creatingProcessRecord(
      final MutableZeebeState zeebeState, final String processId, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("test", task -> task.zeebeJobType("type"))
            .endEvent()
            .done();

    final ProcessRecord processRecord = new ProcessRecord();
    final String resourceName = "process.bpmn";
    final var resource = wrapString(Bpmn.convertToString(modelInstance));
    final var checksum = wrapString("checksum");

    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();
    final long key = keyGenerator.nextKey();

    processRecord
        .setResourceName(wrapString(resourceName))
        .setResource(resource)
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(key)
        .setResourceName(resourceName)
        .setChecksum(checksum);

    return processRecord;
  }
}
