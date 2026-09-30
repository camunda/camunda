/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Awaiting a result over REST must be bounded by the request's own timeout, not by the servlet
 * container's async timeout: when the container cuts the request off first, the client retries the
 * 503 and creates the process instance a second time. The container's timeout is shortened here so
 * that the tests don't have to wait out its 30 s default.
 */
@ZeebeIntegration
final class CreateProcessInstanceWithResultOverRestTimeoutTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withProperty("spring.mvc.async.request-timeout", "1s");

  @AutoClose private CamundaClient client;
  private String processId;

  @BeforeEach
  void init() {
    client = ZEEBE.newClientBuilder().preferRestOverGrpc(true).build();
    processId = "process-" + UUID.randomUUID();
  }

  @Test
  void shouldAwaitResultForLongerThanTheServletAsyncTimeout() {
    // given
    deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent("wait", e -> e.timerWithDuration("PT3S"))
            .endEvent()
            .done());

    // when
    final ProcessInstanceResult result =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .requestTimeout(Duration.ofSeconds(20))
            .send()
            .join();

    // then
    assertThat(createdInstances()).containsExactly(result.getProcessInstanceKey());
  }

  @Test
  void shouldRespondWithGatewayTimeoutWithoutCreatingTheInstanceAgain() {
    // given
    deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(processId))
            .endEvent()
            .done());

    // when
    final var request =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .requestTimeout(Duration.ofSeconds(3))
            .send();

    // then
    assertThatThrownBy(request::join)
        .isInstanceOf(ProblemException.class)
        .extracting(e -> ((ProblemException) e).code())
        .isEqualTo(504);
    assertThat(createdInstances()).hasSize(1);
  }

  private void deploy(final BpmnModelInstance process) {
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();
  }

  private List<Long> createdInstances() {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.PROCESS_INSTANCE_CREATION)
        .filter(r -> r.getIntent() == ProcessInstanceCreationIntent.CREATED)
        .map(Record::getValue)
        .map(ProcessInstanceCreationRecordValue.class::cast)
        .filter(v -> processId.equals(v.getBpmnProcessId()))
        .map(ProcessInstanceCreationRecordValue::getProcessInstanceKey)
        .toList();
  }
}
