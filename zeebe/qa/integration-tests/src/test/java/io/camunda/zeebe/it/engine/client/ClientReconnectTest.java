/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ClientReconnectTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  private CamundaClient client;
  private long processDefinitionKey;

  @BeforeEach
  void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    processDefinitionKey =
        new ZeebeResourcesHelper(client)
            .deployProcess(Bpmn.createExecutableProcess("process").startEvent().endEvent().done());
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldTransparentlyReconnectOnUnexpectedConnectionLoss() {
    // given
    createProcessInstance(processDefinitionKey);

    // when
    zeebe.stop();

    assertThatThrownBy(() -> createProcessInstance(processDefinitionKey))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("io exception");

    zeebe.start();

    TestUtil.doRepeatedly(
            () -> {
              try {
                return createProcessInstance(processDefinitionKey);
              } catch (final ClientException e) {
                // ignore failures until broker is up again
                return -1L;
              }
            })
        .until(key -> key > 0);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .filterRootScope()
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  private long createProcessInstance(final long processDefinitionKey) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
