/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import org.camunda.community.migration.adapter.CamundaPlatform7AdapterTest.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;


@SpringBootTest(classes = Config.class, properties = "logging.level.root=INFO")
@CamundaSpringProcessTest
public class CamundaPlatform7AdapterTest {

  @Autowired private CamundaClient camundaClient;

  @BeforeEach
  public void setup() {
    SampleBean.executionReceived = false;
    SampleBean.someVariableReceived = false;
    SampleDelegate.canReachExecutionVariable = false;
    SampleDelegate.capturedBusinessKey = null;
    SampleDelegate.capturedVariable = null;
    SampleDelegate.executed = false;
    SampleDelegateBean.canReachExecutionVariable = false;
    SampleDelegateBean.capturedBusinessKey = null;
    SampleDelegateBean.capturedVariable = null;
    SampleDelegateBean.executed = false;
  }

  @Test
  public void testDelegateClass() {
    final BpmnModelInstance bpmn =
        Bpmn.createExecutableProcess("test")
            .startEvent()
            .serviceTask()
            .zeebeJobType("camunda-7-adapter")
            .zeebeTaskHeader("class", SampleDelegate.class.getName())
            .endEvent()
            .done();

    camundaClient.newDeployResourceCommand().addProcessModel(bpmn, "test.bpmn").send().join();
    final VariableDto variableValue = new VariableDto();
    variableValue.setValue("value");
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test")
            .latestVersion()
            .variables(Collections.singletonMap("someVariable", variableValue))
            .send()
            .join();
    CamundaAssert.assertThat(processInstance).isCompleted();

    assertTrue(SampleDelegate.executed);
    assertFalse(SampleDelegate.canReachExecutionVariable);
    assertNotNull(SampleDelegate.capturedVariable);
    assertEquals("42", SampleDelegate.capturedBusinessKey);
  }

  @Test
  public void testDelegateExpression() {
    final BpmnModelInstance bpmn =
        Bpmn.createExecutableProcess("test2")
            .startEvent()
            .serviceTask()
            .zeebeJobType("camunda-7-adapter")
            .zeebeTaskHeader("delegateExpression", "${delegateBean}")
            .endEvent()
            .done();

    camundaClient.newDeployResourceCommand().addProcessModel(bpmn, "test.bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test2")
            .latestVersion()
            .variables(Collections.singletonMap("someVariable", "value"))
            .send()
            .join();
    CamundaAssert.assertThat(processInstance).isCompleted();

    assertTrue(SampleDelegateBean.executed);
    assertFalse(SampleDelegateBean.canReachExecutionVariable);
    assertEquals("value", SampleDelegateBean.capturedVariable);
    assertEquals("42", SampleDelegateBean.capturedBusinessKey);
  }

  @Test
  public void testExpression() {
    final BpmnModelInstance bpmn =
        Bpmn.createExecutableProcess("test2")
            .startEvent()
            .serviceTask()
            .zeebeJobType("camunda-7-adapter")
            .zeebeTaskHeader("expression", "${sampleBean.doStuff(execution,someVariable)}")
            .endEvent()
            .done();

    camundaClient.newDeployResourceCommand().addProcessModel(bpmn, "test.bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test2")
            .latestVersion()
            .variables(Collections.singletonMap("someVariable", "value"))
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).isCompleted();

    assertTrue(SampleBean.executionReceived);
    assertTrue(SampleBean.someVariableReceived);
  }

  @Test
  public void testExternalTaskHandlerWrapper() {
    final BpmnModelInstance bpmn =
        Bpmn.createExecutableProcess("test2")
            .startEvent()
            .serviceTask()
            .zeebeJobType("test-topic")
            .endEvent()
            .done();

    camundaClient.newDeployResourceCommand().addProcessModel(bpmn, "test.bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test2")
            .latestVersion()
            .variables(Collections.singletonMap("someVariable", "value"))
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).isCompleted();
    assertEquals("value", SampleExternalTaskHandler.someVariable);
  }

  @Test
  void testBpmnError() {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-with-error-event.bpmn")
        .send()
        .join();
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("error-test")
            .latestVersion()
            .send()
            .join();
    CamundaAssert.assertThat(processInstance).isCompleted();
    assertTrue(SampleDelegateBean.executed);
  }

  @Import({CamundaPlatform7AdapterConfig.class})
  static class Config {
    @Bean
    public MeterRegistry meterRegistry() { // TODO what's used for?
      return new SimpleMeterRegistry();
    }
  }
}
