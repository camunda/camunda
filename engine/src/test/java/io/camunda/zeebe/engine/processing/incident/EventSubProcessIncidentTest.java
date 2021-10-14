/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventSubProcessIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "proc";
  private static final String JOB_TYPE = "type";
  private static String messageName;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  @Parameterized.Parameter public String testName;

  @Parameterized.Parameter(1)
  public Function<StartEventBuilder, StartEventBuilder> builder;

  @Parameterized.Parameter(2)
  public Consumer<Long> triggerEventSubprocess;

  private ProcessMetadataValue currentProcess;

  @Parameterized.Parameters(name = "{0} event subprocess")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "timer",
        eventSubprocess(s -> s.timerWithDuration("PT60S")),
        eventTrigger(
            key -> {
              assertThat(
                      RecordingExporter.timerRecords(TimerIntent.CREATED)
                          .withProcessInstanceKey(key)
                          .exists())
                  .describedAs("Expected timer to exist")
                  .isTrue();
              ENGINE.increaseTime(Duration.ofSeconds(60));
            })
      },
      {
        "message",
        eventSubprocess(
            s -> s.message(b -> b.name(messageName).zeebeCorrelationKeyExpression("key"))),
        eventTrigger(
            key -> {
              RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                  .withProcessInstanceKey(key)
                  .withMessageName(messageName)
                  .await();
              ENGINE.message().withName(messageName).withCorrelationKey("123").publish();
            })
      },
      {
        "error",
        eventSubprocess(s -> s.error("ERROR")),
        eventTrigger(
            key ->
                ENGINE.job().ofInstance(key).withType(JOB_TYPE).withErrorCode("ERROR").throwError())
      },
    };
  }

  private static Function<StartEventBuilder, StartEventBuilder> eventSubprocess(
      final Function<StartEventBuilder, StartEventBuilder> consumer) {
    return consumer;
  }

  private static Consumer<Long> eventTrigger(final Consumer<Long> eventTrigger) {
    return eventTrigger;
  }

  @Before
  public void init() {
    messageName = helper.getMessageName();
  }

  @Test
  public void shouldCreateIncidentForInputMappingFailure() {
    // given
    final BpmnModelInstance model = process(withEventSubprocessAndInputMapping(builder));

    // when
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    // then
    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("event_sub_proc")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final IncidentRecordValue incidentEventValue = incidentEvent.getValue();
    Assertions.assertThat(incidentEventValue)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessDefinitionKey(currentProcess.getProcessDefinitionKey())
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("event_sub_proc")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(incidentEventValue.getErrorMessage())
        .contains("no variable found for name 'source'");
  }

  @Test
  public void shouldResolveIncidentForInputMappingFailure() {
    // given
    final BpmnModelInstance model = process(withEventSubprocessAndInputMapping(builder));
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(incidentEvent.getValue().getVariableScopeKey())
        .withDocument(Map.of("source", "null"))
        .update();

    // then
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    RecordingExporter.processInstanceRecords()
        .withElementId("event_sub_start")
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  @Test
  public void shouldRecreateIncidentOnResolveIncidentWithoutUpdateVariables() {
    // given
    final BpmnModelInstance model = process(withEventSubprocessAndInputMapping(builder));
    final long processInstanceKey = createInstanceAndTriggerEvent(model);

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final var incidentCount =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .count();
    assertThat(incidentCount).isEqualTo(2);
  }

  private long createInstanceAndTriggerEvent(final BpmnModelInstance model) {
    final long wfInstanceKey = createInstanceAndWaitForTask(model);
    triggerEventSubprocess.accept(wfInstanceKey);
    return wfInstanceKey;
  }

  private long createInstanceAndWaitForTask(final BpmnModelInstance model) {
    currentProcess =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final long wfInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123))
            .create();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(wfInstanceKey)
                .exists())
        .describedAs("Expected job to be created")
        .isTrue();
    return wfInstanceKey;
  }

  private static BpmnModelInstance process(final ProcessBuilder processBuilder) {
    return processBuilder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
        .endEvent("end_proc")
        .done();
  }

  private static ProcessBuilder withEventSubprocessAndInputMapping(
      final Function<StartEventBuilder, StartEventBuilder> builder) {
    final ProcessBuilder process = Bpmn.createExecutableProcess(PROCESS_ID);

    builder
        .apply(
            process
                .eventSubProcess("event_sub_proc")
                .zeebeInputExpression("=source", "localScope")
                .startEvent("event_sub_start")
                .interrupting(true))
        .endEvent("event_sub_end");

    return process;
  }
}
