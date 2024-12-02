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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState.PersistedProcessVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.LongConsumer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessStateTest {

  private static final Long FIRST_PROCESS_KEY =
      Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1);
  private static final String TENANT_ID = "defaultTenant";
  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableProcessState processState;
  private MutableProcessingState processingState;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    processState = processingState.getProcessState();
  }

  @Test
  public void shouldGetInitialProcessVersion() {
    // given

    // when
    final long nextProcessVersion = processState.getLatestProcessVersion("foo", TENANT_ID);

    // then
    assertThat(nextProcessVersion).isZero();
  }

  @Test
  public void shouldGetProcessVersion() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    final long processVersion = processState.getLatestProcessVersion("processId", TENANT_ID);

    // then
    assertThat(processVersion).isEqualTo(1L);
  }

  @Test
  public void shouldIncrementProcessVersion() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);

    final var processRecord2 = creatingProcessRecord(processingState);
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // when
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // then
    final long processVersion = processState.getLatestProcessVersion("processId", TENANT_ID);
    assertThat(processVersion).isEqualTo(2L);
  }

  @Test
  public void shouldNotIncrementProcessVersionForDifferentProcessId() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);
    final var processRecord2 = creatingProcessRecord(processingState, "other");

    // when
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // then
    final long processVersion = processState.getLatestProcessVersion("processId", TENANT_ID);
    assertThat(processVersion).isEqualTo(1L);
    final long otherversion = processState.getLatestProcessVersion("other", TENANT_ID);
    assertThat(otherversion).isEqualTo(1L);
  }

  @Test
  public void shouldGetInitialNextProcessVersion() {
    // given

    // when
    final long nextProcessVersion = processState.getNextProcessVersion("foo", TENANT_ID);

    // then
    assertThat(nextProcessVersion).isEqualTo(1L);
  }

  @Test
  public void shouldGetNextProcessVersion() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    final long processVersion = processState.getNextProcessVersion("processId", TENANT_ID);

    // then
    assertThat(processVersion).isEqualTo(2L);
  }

  @Test
  public void shouldIncrementNextProcessVersion() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);

    final var processRecord2 = creatingProcessRecord(processingState);

    // when
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // then
    final long processVersion = processState.getNextProcessVersion("processId", TENANT_ID);
    assertThat(processVersion).isEqualTo(3L);
  }

  @Test
  public void shouldNotIncrementOnNextProcessVersionOnDuplicate() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final long processVersion = processState.getNextProcessVersion("processId", TENANT_ID);
    assertThat(processVersion).isEqualTo(2L);
  }

  @Test
  public void shouldFindPreviousProcessVersion() {
    // given
    final var versionOneRecord = creatingProcessRecord(processingState).setVersion(1);
    final var versionTwoRecord = creatingProcessRecord(processingState).setVersion(2);
    processState.putProcess(versionOneRecord.getKey(), versionOneRecord);
    processState.putProcess(versionTwoRecord.getKey(), versionTwoRecord);

    // when
    final var processVersion = processState.findProcessVersionBefore("processId", 2, TENANT_ID);

    // then
    assertThat(processVersion).isNotEmpty();
    assertThat(processVersion.get()).isEqualTo(1);
  }

  @Test
  public void shouldReturnEmptyOptionalWhenFindingVersionWithoutPrevious() {
    // given
    final var versionOneRecord = creatingProcessRecord(processingState).setVersion(1);
    processState.putProcess(versionOneRecord.getKey(), versionOneRecord);

    // when
    final var processVersion = processState.findProcessVersionBefore("processId", 1, TENANT_ID);

    // then
    assertThat(processVersion).isEmpty();
  }

  @Test
  public void shouldReturnEmptyOptionalWhenGivenVersionIsNotKnown() {
    // given
    final var versionOneRecord = creatingProcessRecord(processingState).setVersion(1);
    processState.putProcess(versionOneRecord.getKey(), versionOneRecord);

    // when
    final var processVersion = processState.findProcessVersionBefore("processId", 2, TENANT_ID);

    // then
    assertThat(processVersion).isEmpty();
  }

  @Test
  public void shouldNotIncrementNextProcessVersionForDifferentProcessId() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);
    final var processRecord2 = creatingProcessRecord(processingState, "other");

    // when
    processState.putProcess(processRecord2.getKey(), processRecord2);

    // then
    final long processVersion = processState.getNextProcessVersion("processId", TENANT_ID);
    assertThat(processVersion).isEqualTo(2L);
    final long otherversion = processState.getNextProcessVersion("other", TENANT_ID);
    assertThat(otherversion).isEqualTo(2L);
  }

  @Test
  // Regression test for https://github.com/camunda/camunda/issues/14309#issuecomment-1731052065
  public void shouldStoreVersionsInCacheSeparately() {
    // given
    final var process1V1 = creatingProcessRecord(processingState, "process1").setVersion(1);
    final var process2V1 = creatingProcessRecord(processingState, "process2").setVersion(1);
    final var process2V2 = creatingProcessRecord(processingState, "process2").setVersion(2);
    processState.putProcess(process1V1.getKey(), process1V1);
    processState.putProcess(process2V1.getKey(), process2V1);
    processState.putProcess(process2V2.getKey(), process2V2);

    // when
    // After clearing the cache we must get the object from the state to ensure that they are
    // available in the cache.
    processState.clearCache();
    processState.getNextProcessVersion("process1", TENANT_ID);
    processState.getNextProcessVersion("process2", TENANT_ID);

    // then
    // The processes in the cache should not reference the same object.
    assertThat(processState.getNextProcessVersion("process1", TENANT_ID)).isEqualTo(2);
  }

  @Test
  public void shouldReturnNullOnGetLatest() {
    // given

    // when
    final DeployedProcess deployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("deployedProcess"), TENANT_ID);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnNullOnGetProcessByKey() {
    // given

    // when
    final DeployedProcess deployedProcess = processState.getProcessByKeyAndTenant(0, TENANT_ID);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnNullOnGetProcessByProcessIdAndVersion() {
    // given

    // when
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("foo"), 0, TENANT_ID);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnNullOnGetProcessByProcessIdAndDeploymentKey() {
    // given

    // when
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndDeploymentKey(wrapString("foo"), 0, TENANT_ID);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldReturnNullOnGetProcessByProcessIdAndVersionTag() {
    // given

    // when
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersionTag(wrapString("foo"), "v1.0", TENANT_ID);

    // then
    Assertions.assertThat(deployedProcess).isNull();
  }

  @Test
  public void shouldPutDeploymentToState() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(processingState);

    // when
    processState.putDeployment(deploymentRecord);

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);

    Assertions.assertThat(deployedProcess).isNotNull();
  }

  @Test
  public void shouldPutProcessToState() {
    // given
    final var processRecord = creatingProcessRecord(processingState);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final var deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);

    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getKey()).isEqualTo(processRecord.getKey());
    assertThat(deployedProcess.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
    assertThat(deployedProcess.getDeploymentKey()).isEqualTo(processRecord.getDeploymentKey());

    final var processByKey =
        processState.getProcessByKeyAndTenant(processRecord.getKey(), processRecord.getTenantId());
    assertThat(processByKey).isNotNull();
    assertThat(processByKey.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(processByKey.getVersion()).isEqualTo(1);
    assertThat(processByKey.getKey()).isEqualTo(processRecord.getKey());
    assertThat(processByKey.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(processByKey.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
    assertThat(processByKey.getDeploymentKey()).isEqualTo(processRecord.getDeploymentKey());
  }

  @Test
  public void shouldStoreProcessDefinitionKeyByProcessIdAndDeploymentKey() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(processRecord);

    // then
    final var deployedProcess =
        processState.getProcessByProcessIdAndDeploymentKey(
            wrapString("processId"), processRecord.getDeploymentKey(), TENANT_ID);
    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getKey()).isEqualTo(processRecord.getKey());
    assertThat(deployedProcess.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
    assertThat(deployedProcess.getDeploymentKey()).isEqualTo(processRecord.getDeploymentKey());
  }

  @Test
  public void shouldStoreProcessDefinitionKeyByProcessIdAndVersionTag() {
    // given
    final var processId = "processId";
    final var versionTag = "v1.0";
    final var processRecord = creatingProcessRecord(processingState, processId, 1, versionTag);
    processState.putProcess(processRecord.getKey(), processRecord);

    // when
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);

    // then
    final var deployedProcess =
        processState.getProcessByProcessIdAndVersionTag(
            wrapString(processId), versionTag, TENANT_ID);
    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString(processId));
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getKey()).isEqualTo(processRecord.getKey());
    assertThat(deployedProcess.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
    assertThat(deployedProcess.getDeploymentKey()).isEqualTo(processRecord.getDeploymentKey());
    assertThat(deployedProcess.getVersionTag()).isEqualTo(versionTag);
  }

  @Test
  public void shouldNotStoreProcessDefinitionKeyByProcessIdAndVersionTagIfVersionTagIsEmpty() {
    // given
    final var processRecord = creatingProcessRecord(processingState);
    processState.putProcess(processRecord.getKey(), processRecord);
    assertThat(processRecord.getVersionTag()).isEmpty();

    // when
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);

    // then
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                wrapString("processId"), processRecord.getVersionTag(), TENANT_ID))
        .isNull();
  }

  @Test
  public void shouldUpdateLatestDigestOnPutProcessToState() {
    // given
    final var processRecord = creatingProcessRecord(processingState);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final var checksum = processState.getLatestVersionDigest(wrapString("processId"), TENANT_ID);
    assertThat(checksum).isEqualTo(processRecord.getChecksumBuffer());
  }

  @Test
  public void shouldUpdateLatestProcessOnPutProcessToState() {
    // given
    final var processRecord = creatingProcessRecord(processingState);

    // when
    processState.putProcess(processRecord.getKey(), processRecord);

    // then
    final DeployedProcess deployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"), TENANT_ID);

    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString("processId"));
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getKey()).isEqualTo(processRecord.getKey());
    assertThat(deployedProcess.getResource()).isEqualTo(processRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName()).isEqualTo(processRecord.getResourceNameBuffer());
    assertThat(deployedProcess.getDeploymentKey()).isEqualTo(processRecord.getDeploymentKey());
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(processingState);

    // when
    processState.putDeployment(deploymentRecord);
    deploymentRecord.processesMetadata().iterator().next().setKey(212).setBpmnProcessId("other");

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);

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
    processState.putDeployment(creatingDeploymentRecord(processingState));
    processState.putDeployment(creatingDeploymentRecord(processingState));

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);

    final DeployedProcess secondProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 2, TENANT_ID);

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
    processState.putDeployment(creatingDeploymentRecord(processingState));

    // when
    processState.putDeployment(creatingDeploymentRecord(processingState, "otherId"));

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);

    final DeployedProcess secondProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("otherId"), 1, TENANT_ID);

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
    processState.putDeployment(creatingDeploymentRecord(processingState));
    processState.putDeployment(creatingDeploymentRecord(processingState));

    // when
    final DeployedProcess latestProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"), TENANT_ID);

    // then
    final DeployedProcess firstProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);
    final DeployedProcess secondProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 2, TENANT_ID);

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
    processState.putDeployment(creatingDeploymentRecord(processingState));
    final DeployedProcess firstLatest =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"), TENANT_ID);

    // when
    processState.putDeployment(creatingDeploymentRecord(processingState));

    // then
    final DeployedProcess latestProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"), TENANT_ID);

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
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(processingState);
    processState.putDeployment(deploymentRecord);

    // when
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString("processId"), 1, TENANT_ID);

    // then
    final ExecutableProcess process = deployedProcess.getProcess();
    Assertions.assertThat(process).isNotNull();
    final AbstractFlowElement serviceTask = process.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetExecutableProcessByKey() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(processingState);
    processState.putDeployment(deploymentRecord);

    // when
    final long processDefinitionKey = FIRST_PROCESS_KEY;
    final DeployedProcess deployedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, deploymentRecord.getTenantId());

    // then
    final ExecutableProcess process = deployedProcess.getProcess();
    Assertions.assertThat(process).isNotNull();
    final AbstractFlowElement serviceTask = process.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetExecutableProcessByLatestProcess() {
    // given
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord(processingState);
    processState.putDeployment(deploymentRecord);

    // when
    final DeployedProcess deployedProcess =
        processState.getLatestProcessVersionByProcessId(wrapString("processId"), TENANT_ID);

    // then
    final ExecutableProcess process = deployedProcess.getProcess();
    Assertions.assertThat(process).isNotNull();
    final AbstractFlowElement serviceTask = process.getElementById(wrapString("test"));
    Assertions.assertThat(serviceTask).isNotNull();
  }

  @Test
  public void shouldGetProcessByProcessIdAndDeploymentKey() {
    // given
    final var process1Version1 =
        creatingProcessRecord(processingState, "process-1", 1).setDeploymentKey(1L);
    final var process2Version1 =
        creatingProcessRecord(processingState, "process-2", 1).setDeploymentKey(1L);
    final var process1Version2 =
        creatingProcessRecord(processingState, "process-1", 2).setDeploymentKey(2L);
    processState.putProcess(process1Version1.getKey(), process1Version1);
    processState.putProcess(process2Version1.getKey(), process2Version1);
    processState.putProcess(process1Version2.getKey(), process1Version2);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(process1Version1);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(process2Version1);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(process1Version2);

    // when
    final var deployedProcess1 =
        processState.getProcessByProcessIdAndDeploymentKey(wrapString("process-1"), 1L, TENANT_ID);
    final var deployedProcess2 =
        processState.getProcessByProcessIdAndDeploymentKey(wrapString("process-2"), 1L, TENANT_ID);
    final var deployedProcess3 =
        processState.getProcessByProcessIdAndDeploymentKey(wrapString("process-1"), 2L, TENANT_ID);
    final var deployedProcess4 =
        processState.getProcessByProcessIdAndDeploymentKey(wrapString("process-2"), 2L, TENANT_ID);

    // then
    assertThat(deployedProcess1)
        .isNotNull()
        .extracting(DeployedProcess::getKey)
        .isEqualTo(process1Version1.getKey());
    assertThat(deployedProcess2)
        .isNotNull()
        .extracting(DeployedProcess::getKey)
        .isEqualTo(process2Version1.getKey());
    assertThat(deployedProcess3)
        .isNotNull()
        .extracting(DeployedProcess::getKey)
        .isEqualTo(process1Version2.getKey());
    assertThat(deployedProcess4).isNull();
  }

  @Test
  public void shouldGetProcessByProcessIdAndVersionTag() {
    // given
    final var processId1 = "process-1";
    final var processId2 = "process-2";
    final var versionTagV1 = "v1.0";
    final var versionTagV2 = "v2.0";
    final var process1Version1 =
        creatingProcessRecord(processingState, processId1, 1, versionTagV1);
    final var process1Version2 =
        creatingProcessRecord(processingState, processId1, 2, versionTagV2);
    final var process2Version1 =
        creatingProcessRecord(processingState, processId2, 1, versionTagV1);
    processState.putProcess(process1Version1.getKey(), process1Version1);
    processState.putProcess(process1Version2.getKey(), process1Version2);
    processState.putProcess(process2Version1.getKey(), process2Version1);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(process1Version1);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(process1Version2);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(process2Version1);

    // when
    final var deployedProcess1 =
        processState.getProcessByProcessIdAndVersionTag(
            wrapString(processId1), versionTagV1, TENANT_ID);
    final var deployedProcess2 =
        processState.getProcessByProcessIdAndVersionTag(
            wrapString(processId1), versionTagV2, TENANT_ID);
    final var deployedProcess3 =
        processState.getProcessByProcessIdAndVersionTag(
            wrapString(processId2), versionTagV1, TENANT_ID);
    final var deployedProcess4 =
        processState.getProcessByProcessIdAndVersionTag(
            wrapString(processId2), versionTagV2, TENANT_ID);

    // then
    assertThat(deployedProcess1)
        .isNotNull()
        .extracting(DeployedProcess::getKey)
        .isEqualTo(process1Version1.getKey());
    assertThat(deployedProcess2)
        .isNotNull()
        .extracting(DeployedProcess::getKey)
        .isEqualTo(process1Version2.getKey());
    assertThat(deployedProcess3)
        .isNotNull()
        .extracting(DeployedProcess::getKey)
        .isEqualTo(process2Version1.getKey());
    assertThat(deployedProcess4).isNull();
  }

  @Test
  public void shouldReturnHighestVersionInsteadOfMostRecent() {
    // given
    final String processId = "process";
    processState.putDeployment(creatingDeploymentRecord(processingState, processId, 2));
    processState.putDeployment(creatingDeploymentRecord(processingState, processId, 1));

    // when
    final DeployedProcess latestProcess =
        processState.getLatestProcessVersionByProcessId(wrapString(processId), TENANT_ID);

    // then
    Assertions.assertThat(latestProcess.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldUpdateProcessState() {
    // given
    final long processDefinitionKey = 100L;
    final var processRecord = creatingProcessRecord(processingState).setKey(processDefinitionKey);
    processState.putProcess(processDefinitionKey, processRecord);
    final var initialProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, processRecord.getTenantId());

    // when
    processState.updateProcessState(processRecord, PersistedProcessState.PENDING_DELETION);

    // then
    assertThat(initialProcess.getState()).isEqualTo(PersistedProcessState.ACTIVE);
    final var updatedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, processRecord.getTenantId());
    assertThat(updatedProcess.getState()).isEqualTo(PersistedProcessState.PENDING_DELETION);
  }

  @Test
  public void shouldDeleteLatestProcess() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final var processRecord = creatingProcessRecord(processingState, processId, 1, "v1.0");
    final var processDefinitionKey = processRecord.getProcessDefinitionKey();
    processState.putProcess(processDefinitionKey, processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(processRecord);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);

    // when
    processState.deleteProcess(processRecord);

    // then
    assertThat(
            processState.getProcessByKeyAndTenant(
                processDefinitionKey, processRecord.getTenantId()))
        .isNull();
    assertThat(
            processState.getLatestProcessVersionByProcessId(
                BufferUtil.wrapString(processId), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 1, TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), processRecord.getDeploymentKey(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), processRecord.getVersionTag(), TENANT_ID))
        .isNull();
    assertThat(processState.getLatestVersionDigest(BufferUtil.wrapString(processId), TENANT_ID))
        .isNull();
    assertThat(processState.getLatestProcessVersion(processId, TENANT_ID)).isEqualTo(0);
    assertThat(processState.getNextProcessVersion(processId, TENANT_ID)).isEqualTo(2);
  }

  @Test
  public void shouldDeleteOldProcess() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final var oldProcess =
        creatingProcessRecord(processingState, processId, 1, "v1.0")
            .setChecksum(wrapString("oldChecksum"));
    final var newProcess =
        creatingProcessRecord(processingState, processId, 2, "v2.0")
            .setChecksum(wrapString("newChecksum"));
    final var oldDefinitionKey = oldProcess.getProcessDefinitionKey();
    final var newDefinitionKey = newProcess.getProcessDefinitionKey();
    processState.putProcess(oldDefinitionKey, oldProcess);
    processState.putProcess(newDefinitionKey, newProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(oldProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(newProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(oldProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(newProcess);

    // when
    processState.deleteProcess(oldProcess);

    // then
    assertThat(processState.getProcessByKeyAndTenant(oldDefinitionKey, oldProcess.getTenantId()))
        .isNull();
    assertThat(
            processState.getLatestProcessVersionByProcessId(
                BufferUtil.wrapString(processId), TENANT_ID))
        .extracting(DeployedProcess::getKey)
        .isEqualTo(newDefinitionKey);
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 1, TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 2, TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), oldProcess.getDeploymentKey(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), newProcess.getDeploymentKey(), TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), oldProcess.getVersionTag(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), newProcess.getVersionTag(), TENANT_ID))
        .isNotNull();
    assertThat(processState.getLatestVersionDigest(BufferUtil.wrapString(processId), TENANT_ID))
        .isEqualTo(wrapString("newChecksum"));
    assertThat(processState.getLatestProcessVersion(processId, TENANT_ID)).isEqualTo(2);
    assertThat(processState.getNextProcessVersion(processId, TENANT_ID)).isEqualTo(3);
  }

  @Test
  public void shouldDeleteNewProcess() {
    // given
    final String processId = "processId";
    final var oldProcess =
        creatingProcessRecord(processingState, processId, 1, "v1.0")
            .setChecksum(wrapString("oldChecksum"));
    final var newProcess =
        creatingProcessRecord(processingState, processId, 2, "v2.0")
            .setChecksum(wrapString("newChecksum"));
    final var oldDefinitionKey = oldProcess.getProcessDefinitionKey();
    final var newDefinitionKey = newProcess.getProcessDefinitionKey();
    processState.putProcess(oldDefinitionKey, oldProcess);
    processState.putProcess(newDefinitionKey, newProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(oldProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(newProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(oldProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(newProcess);

    // when
    processState.deleteProcess(newProcess);

    // then
    assertThat(processState.getProcessByKeyAndTenant(newDefinitionKey, newProcess.getTenantId()))
        .isNull();
    assertThat(
            processState.getLatestProcessVersionByProcessId(
                BufferUtil.wrapString(processId), TENANT_ID))
        .extracting(DeployedProcess::getKey)
        .isEqualTo(oldDefinitionKey);
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 1, TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 2, TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), oldProcess.getDeploymentKey(), TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), newProcess.getDeploymentKey(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), oldProcess.getVersionTag(), TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), newProcess.getVersionTag(), TENANT_ID))
        .isNull();
    assertThat(processState.getLatestVersionDigest(BufferUtil.wrapString(processId), TENANT_ID))
        .isNull();
    assertThat(processState.getLatestProcessVersion(processId, TENANT_ID)).isEqualTo(1);
    assertThat(processState.getNextProcessVersion(processId, TENANT_ID)).isEqualTo(3);
  }

  @Test
  public void shouldDeleteMidProcessBeforeLatestProcess() {
    // given
    final String processId = "processId";
    final var oldProcess =
        creatingProcessRecord(processingState, processId, 1, "v1.0")
            .setChecksum(wrapString("oldChecksum"));
    final var midProcess =
        creatingProcessRecord(processingState, processId, 2, "v2.0")
            .setChecksum(wrapString("midChecksum"));
    final var newProcess =
        creatingProcessRecord(processingState, processId, 3, "v3.0")
            .setChecksum(wrapString("newChecksum"));
    final var oldDefinitionKey = oldProcess.getProcessDefinitionKey();
    final var midDefinitionKey = midProcess.getProcessDefinitionKey();
    final var newDefinitionKey = newProcess.getProcessDefinitionKey();
    processState.putProcess(oldDefinitionKey, oldProcess);
    processState.putProcess(midDefinitionKey, midProcess);
    processState.putProcess(newDefinitionKey, newProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(oldProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(midProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(newProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(oldProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(midProcess);
    processState.storeProcessDefinitionKeyByProcessIdAndVersionTag(newProcess);

    // when
    processState.deleteProcess(midProcess);
    processState.deleteProcess(newProcess);

    // then
    assertThat(processState.getProcessByKeyAndTenant(midDefinitionKey, midProcess.getTenantId()))
        .isNull();
    assertThat(processState.getProcessByKeyAndTenant(newDefinitionKey, midProcess.getTenantId()))
        .isNull();
    assertThat(
            processState.getLatestProcessVersionByProcessId(
                BufferUtil.wrapString(processId), TENANT_ID))
        .extracting(DeployedProcess::getKey)
        .isEqualTo(oldDefinitionKey);
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 1, TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 2, TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersion(
                BufferUtil.wrapString(processId), 3, TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), oldProcess.getDeploymentKey(), TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), midProcess.getDeploymentKey(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndDeploymentKey(
                BufferUtil.wrapString(processId), newProcess.getDeploymentKey(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), oldProcess.getVersionTag(), TENANT_ID))
        .isNotNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), midProcess.getVersionTag(), TENANT_ID))
        .isNull();
    assertThat(
            processState.getProcessByProcessIdAndVersionTag(
                BufferUtil.wrapString(processId), newProcess.getVersionTag(), TENANT_ID))
        .isNull();
    assertThat(processState.getLatestVersionDigest(BufferUtil.wrapString(processId), TENANT_ID))
        .isNull();
    assertThat(processState.getLatestProcessVersion(processId, TENANT_ID)).isEqualTo(1);
    assertThat(processState.getNextProcessVersion(processId, TENANT_ID)).isEqualTo(4);
  }

  @Test
  public void shouldAddProcessAfterOnlyVersionIsDeleted() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final var oldProcessRecord = creatingProcessRecord(processingState, processId, 1);
    final var oldProcessDefinitionKey = oldProcessRecord.getProcessDefinitionKey();
    final var newProcessRecord = creatingProcessRecord(processingState, processId, 2);
    final var newProcessDefinitionKey = newProcessRecord.getProcessDefinitionKey();
    processState.putProcess(oldProcessDefinitionKey, oldProcessRecord);
    processState.deleteProcess(oldProcessRecord);

    // when
    processState.putProcess(newProcessDefinitionKey, newProcessRecord);

    // then
    final DeployedProcess deployedProcess =
        processState.getProcessByProcessIdAndVersion(wrapString(processId), 2, TENANT_ID);

    assertThat(deployedProcess).isNotNull();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(wrapString(processId));
    assertThat(deployedProcess.getVersion()).isEqualTo(2);
    assertThat(deployedProcess.getKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(deployedProcess.getResource()).isEqualTo(newProcessRecord.getResourceBuffer());
    assertThat(deployedProcess.getResourceName())
        .isEqualTo(newProcessRecord.getResourceNameBuffer());

    final var processByKey =
        processState.getProcessByKeyAndTenant(
            newProcessRecord.getKey(), newProcessRecord.getTenantId());
    assertThat(processByKey).isNotNull();
    assertThat(processByKey.getBpmnProcessId()).isEqualTo(wrapString(processId));
    assertThat(processByKey.getVersion()).isEqualTo(2);
    assertThat(processByKey.getKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(processByKey.getResource()).isEqualTo(newProcessRecord.getResourceBuffer());
    assertThat(processByKey.getResourceName()).isEqualTo(newProcessRecord.getResourceNameBuffer());

    assertThat(processState.getLatestProcessVersion(processId, TENANT_ID)).isEqualTo(2);
    assertThat(processState.getNextProcessVersion(processId, TENANT_ID)).isEqualTo(3);
  }

  @Test
  public void shouldIterateThroughAllProcesses() {
    // given
    final var process1 = creatingProcessRecord(processingState, "process1", 1);
    final var process2 = creatingProcessRecord(processingState, "process2", 1);
    final var process3 = creatingProcessRecord(processingState, "process3", 1);
    processState.putProcess(process1.getKey(), process1);
    processState.putProcess(process2.getKey(), process2);
    processState.putProcess(process3.getKey(), process3);
    final var visitor = mock(LongConsumer.class);

    // when
    processState.forEachProcess(
        null,
        (process) -> {
          visitor.accept(process.getKey());
          return true;
        });

    // then
    verify(visitor).accept(process1.getKey());
    verify(visitor).accept(process2.getKey());
    verify(visitor).accept(process3.getKey());
  }

  @Test
  public void shouldSkipBeforeIteratingThroughProcesses() {
    // given
    final var process1 = creatingProcessRecord(processingState, "process1", 1);
    final var process2 = creatingProcessRecord(processingState, "process2", 1);
    final var process3 = creatingProcessRecord(processingState, "process3", 1);
    processState.putProcess(process1.getKey(), process1);
    processState.putProcess(process2.getKey(), process2);
    processState.putProcess(process3.getKey(), process3);
    final var visitor = mock(PersistedProcessVisitor.class);
    when(visitor.visit(any())).thenReturn(true);

    // when
    processState.forEachProcess(
        new ProcessIdentifier(process1.getTenantId(), process1.getProcessDefinitionKey()), visitor);

    // then
    verify(visitor, times(2)).visit(any());
  }

  @Test
  public void shouldSetDeploymentKey() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;
    final var process1 =
        creatingProcessRecord(processingState, "process1", 1).setDeploymentKey(noDeploymentKey);
    processState.putProcess(process1.getKey(), process1);
    final var initialDeploymentKey =
        processState
            .getProcessByKeyAndTenant(process1.getKey(), process1.getTenantId())
            .getDeploymentKey();

    // when
    processState.setMissingDeploymentKey(process1.getTenantId(), process1.getKey(), 1L);

    // then
    final var updatedDeploymentKey =
        processState
            .getProcessByKeyAndTenant(process1.getKey(), process1.getTenantId())
            .getDeploymentKey();

    assertThat(initialDeploymentKey).isEqualTo(noDeploymentKey);
    assertThat(updatedDeploymentKey).isEqualTo(someDeploymentKey);
  }

  @Test
  public void shouldSetDeploymentKeyPersistently() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;
    final var process =
        creatingProcessRecord(processingState, "process1", 1).setDeploymentKey(noDeploymentKey);
    processState.putProcess(process.getKey(), process);
    final var initialDeploymentKey =
        processState
            .getProcessByKeyAndTenant(process.getKey(), process.getTenantId())
            .getDeploymentKey();
    processState.setMissingDeploymentKey(process.getTenantId(), process.getKey(), 1L);

    // when
    processState.clearCache();

    // then
    final var updatedDeploymentKey =
        processState
            .getProcessByKeyAndTenant(process.getKey(), process.getTenantId())
            .getDeploymentKey();

    assertThat(initialDeploymentKey).isEqualTo(noDeploymentKey);
    assertThat(updatedDeploymentKey).isEqualTo(someDeploymentKey);
  }

  @Test
  public void shouldFindProcessByDeploymentKeyAfterUpdating() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;
    final var process =
        creatingProcessRecord(processingState, "process1", 1).setDeploymentKey(noDeploymentKey);
    processState.putProcess(process.getKey(), process);

    final var initialLookupByDeploymentKey =
        processState.getProcessByProcessIdAndDeploymentKey(
            process.getBpmnProcessIdBuffer(), someDeploymentKey, process.getTenantId());

    // when
    processState.setMissingDeploymentKey(process.getTenantId(), process.getKey(), 1L);

    // then
    final var lookupAfterUpdating =
        processState.getProcessByProcessIdAndDeploymentKey(
            process.getBpmnProcessIdBuffer(), someDeploymentKey, process.getTenantId());

    assertThat(initialLookupByDeploymentKey).isNull();
    assertThat(lookupAfterUpdating.getKey()).isEqualTo(process.getKey());
  }

  public static DeploymentRecord creatingDeploymentRecord(
      final MutableProcessingState processingState) {
    return creatingDeploymentRecord(processingState, "processId");
  }

  public static DeploymentRecord creatingDeploymentRecord(
      final MutableProcessingState processingState, final String processId) {
    final MutableProcessState processState = processingState.getProcessState();
    final int version = processState.getNextProcessVersion(processId, TENANT_ID);
    return creatingDeploymentRecord(processingState, processId, version);
  }

  public static DeploymentRecord creatingDeploymentRecord(
      final MutableProcessingState processingState, final String processId, final int version) {
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
        .setTenantId(TENANT_ID)
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(resource);

    final KeyGenerator keyGenerator = processingState.getKeyGenerator();
    final long key = keyGenerator.nextKey();

    deploymentRecord
        .processesMetadata()
        .add()
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(key)
        .setResourceName(resourceName)
        .setChecksum(checksum)
        .setTenantId(TENANT_ID);

    return deploymentRecord;
  }

  public static ProcessRecord creatingProcessRecord(final MutableProcessingState processingState) {
    return creatingProcessRecord(processingState, "processId");
  }

  public static ProcessRecord creatingProcessRecord(
      final MutableProcessingState processingState, final String processId) {
    final MutableProcessState processState = processingState.getProcessState();
    final int version = processState.getNextProcessVersion(processId, TENANT_ID);
    return creatingProcessRecord(processingState, processId, version, null);
  }

  public static ProcessRecord creatingProcessRecord(
      final MutableProcessingState processingState, final String processId, final int version) {
    return creatingProcessRecord(processingState, processId, version, null);
  }

  public static ProcessRecord creatingProcessRecord(
      final MutableProcessingState processingState,
      final String processId,
      final int version,
      final String versionTag) {
    final var processBuilder = Bpmn.createExecutableProcess(processId);
    if (versionTag != null) {
      processBuilder.versionTag(versionTag);
    }
    final BpmnModelInstance modelInstance =
        processBuilder
            .startEvent("startEvent")
            .serviceTask("test", task -> task.zeebeJobType("type"))
            .endEvent("endEvent")
            .done();
    final ProcessRecord processRecord = new ProcessRecord();
    final String resourceName = "process.bpmn";
    final var resource = wrapString(Bpmn.convertToString(modelInstance));
    final var checksum = wrapString("checksum");

    final KeyGenerator keyGenerator = processingState.getKeyGenerator();
    final long key = keyGenerator.nextKey();

    processRecord
        .setResourceName(wrapString(resourceName))
        .setResource(resource)
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setVersion(version)
        .setKey(key)
        .setResourceName(resourceName)
        .setChecksum(checksum)
        .setTenantId(TENANT_ID)
        .setDeploymentKey(keyGenerator.nextKey());
    if (versionTag != null) {
      processRecord.setVersionTag(versionTag);
    }

    return processRecord;
  }
}
