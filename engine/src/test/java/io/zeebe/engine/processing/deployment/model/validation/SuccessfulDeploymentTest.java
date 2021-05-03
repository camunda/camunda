/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.client.DeploymentClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class SuccessfulDeploymentTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String description;

  @Parameter(1)
  public Function<DeploymentClient, Record<DeploymentRecordValue>> performDeployment;

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[] {"orphan error definition", deploy("/processes/orphan-error-definition.bpmn")},
        new Object[] {
          "none start event",
          deploy(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        },
        new Object[] {
          "timer start event",
          deploy(
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .timerWithCycle("R/PT10S")
                  .endEvent()
                  .done())
        },
        new Object[] {
          "message start event",
          deploy(
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .message("start")
                  .endEvent()
                  .done())
        });
  }

  @Test
  public void shouldWriteDeploymentCreatedEvent() {
    // when
    final var deployment = performDeployment.apply(engine.deployment());

    // then
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
    assertThat(deployment.getValue().getDeployedProcesses()).hasSize(1);
  }

  @Test
  public void shouldSendResponse() {
    // when
    final var deployment = performDeployment.apply(engine.deployment());

    // then
    verify(engine.getCommandResponseWriter()).recordType(RecordType.EVENT);
    verify(engine.getCommandResponseWriter()).valueType(ValueType.DEPLOYMENT);
    verify(engine.getCommandResponseWriter()).intent(DeploymentIntent.CREATED);
    verify(engine.getCommandResponseWriter()).key(deployment.getKey());
    verify(engine.getCommandResponseWriter()).tryWriteResponse(anyInt(), anyLong());
  }

  private static Function<DeploymentClient, Record<DeploymentRecordValue>> deploy(
      final String resource) {
    return deploymentClient -> deploymentClient.withXmlClasspathResource(resource).deploy();
  }

  private static Function<DeploymentClient, Record<DeploymentRecordValue>> deploy(
      final BpmnModelInstance process) {
    return deploymentClient -> deploymentClient.withXmlResource(process).deploy();
  }
}
