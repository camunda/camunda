/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.ByteValue;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class MultiPartitionRejectionTest {

  @Rule public final EngineRule engine = EngineRule.multiplePartition(3);

  @Test
  public void shouldRejectDeploymentIfResourceIsTooLarge() {
    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("PROCESS")
                    .startEvent()
                    .documentation(
                        "x".repeat((int) (ByteValue.ofMegabytes(2) - ByteValue.ofKilobytes(1))))
                    .done())
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRejectionType(RejectionType.EXCEEDED_BATCH_RECORD_SIZE)
        .hasRejectionReason("");
  }

  @Test // Regression of https://github.com/camunda/camunda/issues/13254
  public void shouldNotBeAbleToCreateInstanceWhenDeploymentIsRejected() {
    // given
    final BpmnModelInstance invalidProcess =
        Bpmn.createExecutableProcess("too_large_process")
            .startEvent()
            // In order to cause BATCH SIZE EXCEEDING we add a big comment
            .documentation("x".repeat((int) (ByteValue.ofMegabytes(2) - ByteValue.ofKilobytes(2))))
            .done();
    final BpmnModelInstance validProcess =
        Bpmn.createExecutableProcess("valid_process").startEvent().task().endEvent().done();

    // when
    engine
        .deployment()
        .withXmlResource(invalidProcess)
        .withXmlResource(validProcess)
        .expectRejection()
        .deploy();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
                .collect(Collectors.toList()))
        .extracting(Record::getIntent, Record::getRecordType)
        .doesNotContain(
            tuple(ProcessIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT));

    engine.processInstance().ofBpmnProcessId("valid_process").expectRejection().create();
  }
}
