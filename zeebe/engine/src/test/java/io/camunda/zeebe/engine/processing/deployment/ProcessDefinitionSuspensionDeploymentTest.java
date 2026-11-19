/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessDefinitionSuspensionDeploymentTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateNewVersionWhenRedeployingIdenticalResourceWhileLatestIsSuspended() {
    // given - the only version is suspended, and the suspension is rebuilt from the log on restart
    final var process = process(Strings.newRandomValidBpmnId());
    final var first = deployedProcess(engine.deployment().withXmlResource(process).deploy());
    write(ProcessIntent.SUSPENDED, first);
    engine.stop();
    engine.start();

    // when
    final var second = deployedProcess(engine.deployment().withXmlResource(process).deploy());

    // then
    assertThat(second.isDuplicate()).isFalse();
    assertThat(second.getVersion()).isEqualTo(first.getVersion() + 1);
    assertThat(second.getProcessDefinitionKey()).isNotEqualTo(first.getProcessDefinitionKey());
  }

  @Test
  public void shouldTreatRedeployedIdenticalResourceAsDuplicateWhileLatestIsNotSuspended() {
    // given
    final var process = process(Strings.newRandomValidBpmnId());
    final var first = deployedProcess(engine.deployment().withXmlResource(process).deploy());

    // when
    final var second = deployedProcess(engine.deployment().withXmlResource(process).deploy());

    // then
    assertThat(second.isDuplicate()).isTrue();
    assertThat(second.getProcessDefinitionKey()).isEqualTo(first.getProcessDefinitionKey());
  }

  @Test
  public void shouldTreatRedeployedIdenticalResourceAsDuplicateAfterTheLatestIsResumed() {
    // given - the only version was suspended and resumed, rebuilt from the log on restart
    final var process = process(Strings.newRandomValidBpmnId());
    final var first = deployedProcess(engine.deployment().withXmlResource(process).deploy());
    write(ProcessIntent.SUSPENDED, first);
    write(ProcessIntent.RESUMED, first);
    engine.stop();
    engine.start();

    // when
    final var second = deployedProcess(engine.deployment().withXmlResource(process).deploy());

    // then
    assertThat(second.isDuplicate()).isTrue();
    assertThat(second.getProcessDefinitionKey()).isEqualTo(first.getProcessDefinitionKey());
  }

  private void write(final ProcessIntent intent, final ProcessMetadataValue process) {
    final var record =
        new ProcessRecord()
            .setKey(process.getProcessDefinitionKey())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setResourceName(process.getResourceName())
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    engine.writeRecords(RecordToWrite.event().process(intent, record));
  }

  private static ProcessMetadataValue deployedProcess(
      final Record<DeploymentRecordValue> deployment) {
    return deployment.getValue().getProcessesMetadata().getFirst();
  }

  private static BpmnModelInstance process(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }
}
