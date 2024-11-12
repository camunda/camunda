/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BpmnEventTypeTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  public static final String CORRELATION_KEY = "key";
  private static final List<BpmnEventTypeScenario> SCENARIOS =
      Arrays.asList(
          new BpmnEventTypeScenario(
              "None Start Event", BpmnElementType.START_EVENT, BpmnEventType.NONE) {

            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId()).startEvent(elementId()).done();
            }
          },
          new BpmnEventTypeScenario(
              "None Throw Event", BpmnElementType.INTERMEDIATE_THROW_EVENT, BpmnEventType.NONE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateThrowEvent(elementId())
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "None End Event", BpmnElementType.END_EVENT, BpmnEventType.NONE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .endEvent(elementId())
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Message Start Event", BpmnElementType.START_EVENT, BpmnEventType.MESSAGE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent(elementId())
                  .message(
                      m -> m.name(messageName()).zeebeCorrelationKeyExpression(CORRELATION_KEY))
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              ENGINE
                  .message()
                  .withName(messageName())
                  .withCorrelationKey(CORRELATION_KEY)
                  .publish();
            }
          },
          new BpmnEventTypeScenario(
              "Message Catch Event",
              BpmnElementType.INTERMEDIATE_CATCH_EVENT,
              BpmnEventType.MESSAGE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateCatchEvent(elementId())
                  .message(
                      m -> m.name(messageName()).zeebeCorrelationKeyExpression(CORRELATION_KEY))
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              executeInstance(Collections.singletonMap(CORRELATION_KEY, "key"));
              ENGINE
                  .message()
                  .withName(messageName())
                  .withCorrelationKey(CORRELATION_KEY)
                  .publish();
            }
          },
          new BpmnEventTypeScenario(
              "Message Throw Event",
              BpmnElementType.INTERMEDIATE_THROW_EVENT,
              BpmnEventType.MESSAGE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateThrowEvent(elementId())
                  .message(messageName())
                  .zeebeJobType(jobType())
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              super.executeInstance();
              final Record<JobRecordValue> job =
                  RecordingExporter.jobRecords()
                      .withElementId(elementId())
                      .withIntent(JobIntent.CREATED)
                      .getFirst();
              ENGINE.job().withKey(job.getKey()).complete();
            }
          },
          new BpmnEventTypeScenario(
              "Message End Event", BpmnElementType.END_EVENT, BpmnEventType.MESSAGE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .endEvent(elementId())
                  .message(messageName())
                  .zeebeJobType(jobType())
                  .done();
            }

            @Override
            void executeInstance() {
              super.executeInstance();
              final Record<JobRecordValue> job =
                  RecordingExporter.jobRecords()
                      .withElementId(elementId())
                      .withIntent(JobIntent.CREATED)
                      .getFirst();
              ENGINE.job().withKey(job.getKey()).complete();
            }
          },
          new BpmnEventTypeScenario(
              "Timer Start Event", BpmnElementType.START_EVENT, BpmnEventType.TIMER) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent(elementId())
                  .timerWithCycle("R1/PT0.01S")
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              ENGINE.increaseTime(Duration.ofMinutes(1));
            }
          },
          new BpmnEventTypeScenario(
              "Timer Catch Event", BpmnElementType.INTERMEDIATE_CATCH_EVENT, BpmnEventType.TIMER) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateCatchEvent(elementId())
                  .timerWithDuration("PT0.01S")
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Error Start Event", BpmnElementType.START_EVENT, BpmnEventType.ERROR) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .eventSubProcess(
                      "subprocess", sp -> sp.startEvent(elementId()).error(errorCode()).endEvent())
                  .startEvent()
                  .serviceTask("task", t -> t.zeebeJobType(jobType()))
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              final long processInstanceKey = super.executeInstance(Collections.emptyMap());
              final Record<JobRecordValue> job =
                  RecordingExporter.jobRecords()
                      .withProcessInstanceKey(processInstanceKey)
                      .withIntent(JobIntent.CREATED)
                      .getFirst();
              ENGINE.job().withKey(job.getKey()).withErrorCode(errorCode()).throwError();
            }
          },
          new BpmnEventTypeScenario(
              "Error Boundary Event", BpmnElementType.BOUNDARY_EVENT, BpmnEventType.ERROR) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .serviceTask("task", t -> t.zeebeJobType(jobType()))
                  .boundaryEvent(elementId())
                  .error(errorCode())
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              final long processInstanceKey = super.executeInstance(Collections.emptyMap());
              final Record<JobRecordValue> job =
                  RecordingExporter.jobRecords()
                      .withProcessInstanceKey(processInstanceKey)
                      .withIntent(JobIntent.CREATED)
                      .getFirst();
              ENGINE.job().withKey(job.getKey()).withErrorCode(errorCode()).throwError();
            }
          },
          new BpmnEventTypeScenario(
              "Error End Event", BpmnElementType.END_EVENT, BpmnEventType.ERROR) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .subProcess(
                      "subProcess",
                      sp ->
                          sp.embeddedSubProcess()
                              .startEvent()
                              .endEvent(elementId(), e -> e.error(errorCode())))
                  .boundaryEvent("boundary", b -> b.error(errorCode()))
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Terminate End Event", BpmnElementType.END_EVENT, BpmnEventType.TERMINATE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .endEvent(elementId())
                  .terminate()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Link Catch Event", BpmnElementType.INTERMEDIATE_CATCH_EVENT, BpmnEventType.LINK) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateCatchEvent(elementId())
                  .link("link")
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Link Throw Event", BpmnElementType.INTERMEDIATE_THROW_EVENT, BpmnEventType.LINK) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateThrowEvent(elementId())
                  .link("link")
                  .intermediateCatchEvent()
                  .link("link")
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Receive Task", BpmnElementType.RECEIVE_TASK, BpmnEventType.MESSAGE) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .receiveTask(
                      elementId(),
                      r ->
                          r.message(
                              m ->
                                  m.name(messageName())
                                      .zeebeCorrelationKeyExpression(CORRELATION_KEY)))
                  .endEvent()
                  .done();
            }

            @Override
            void executeInstance() {
              executeInstance(Collections.singletonMap(CORRELATION_KEY, "key"));
              ENGINE
                  .message()
                  .withName(messageName())
                  .withCorrelationKey(CORRELATION_KEY)
                  .publish();
            }
          },
          new BpmnEventTypeScenario(
              "Escalation Start Event", BpmnElementType.START_EVENT, BpmnEventType.ESCALATION) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .eventSubProcess(
                      "subprocess",
                      sp -> sp.startEvent(elementId()).escalation(escalationCode()).endEvent())
                  .startEvent()
                  .intermediateThrowEvent("throw", i -> i.escalation(escalationCode()))
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Escalation Throw Event",
              BpmnElementType.INTERMEDIATE_THROW_EVENT,
              BpmnEventType.ESCALATION) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateThrowEvent(elementId(), e -> e.escalation(escalationCode()))
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Escalation Boundary Event",
              BpmnElementType.BOUNDARY_EVENT,
              BpmnEventType.ESCALATION) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .subProcess(
                      "subprocess",
                      sp ->
                          sp.embeddedSubProcess()
                              .startEvent()
                              .intermediateThrowEvent("throw", e -> e.escalation(escalationCode()))
                              .endEvent())
                  .boundaryEvent(elementId(), b -> b.escalation(escalationCode()))
                  .cancelActivity(false)
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Escalation End Event", BpmnElementType.END_EVENT, BpmnEventType.ESCALATION) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .endEvent(elementId(), e -> e.escalation(escalationCode()))
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Compensation Intermediate Throw Event",
              BpmnElementType.INTERMEDIATE_THROW_EVENT,
              BpmnEventType.COMPENSATION) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .intermediateThrowEvent(
                      elementId(),
                      t -> t.compensateEventDefinition().compensateEventDefinitionDone())
                  .endEvent()
                  .done();
            }
          },
          new BpmnEventTypeScenario(
              "Compensation End Event", BpmnElementType.END_EVENT, BpmnEventType.COMPENSATION) {
            @Override
            BpmnModelInstance modelInstance() {
              return Bpmn.createExecutableProcess(processId())
                  .startEvent()
                  .endEvent(
                      elementId(),
                      end -> end.compensateEventDefinition().compensateEventDefinitionDone())
                  .done();
            }
          });

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final BpmnEventTypeScenario scenario;

  public BpmnEventTypeTest(final BpmnEventTypeScenario scenario) {
    this.scenario = scenario;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> scenarios() {
    return SCENARIOS.stream().map(s -> new Object[] {s}).collect(Collectors.toList());
  }

  @Test
  public void shouldContainEventTypeInRecord() {
    // given
    ENGINE.deployment().withXmlResource(scenario.modelInstance()).deploy();

    // when
    scenario.executeInstance();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withBpmnProcessId(scenario.processId())
                .limitToProcessInstanceCompleted()
                .withElementId(scenario.elementId()))
        .extracting(r -> r.getValue().getBpmnElementType(), r -> r.getValue().getBpmnEventType())
        .containsOnly(tuple(scenario.elementType, scenario.eventType));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withBpmnProcessId(scenario.processId())
                .limitToProcessInstanceCompleted()
                .filter(r -> !isEvent(r.getValue().getBpmnElementType()))
                .map(r -> r.getValue().getBpmnEventType()))
        .containsOnly(BpmnEventType.UNSPECIFIED);
  }

  private boolean isEvent(final BpmnElementType elementType) {
    return Set.of(
            BpmnElementType.START_EVENT,
            BpmnElementType.INTERMEDIATE_CATCH_EVENT,
            BpmnElementType.INTERMEDIATE_THROW_EVENT,
            BpmnElementType.BOUNDARY_EVENT,
            BpmnElementType.END_EVENT,
            BpmnElementType.RECEIVE_TASK)
        .contains(elementType);
  }

  private abstract static class BpmnEventTypeScenario {
    private final String name;
    private final BpmnElementType elementType;
    private final BpmnEventType eventType;
    private final String processId = Strings.newRandomValidBpmnId();
    private final String elementId = Strings.newRandomValidBpmnId();
    private final String messageName = Strings.newRandomValidBpmnId();
    private final String jobType = Strings.newRandomValidBpmnId();
    private final String errorCode = Strings.newRandomValidBpmnId();
    private final String escalationCode = Strings.newRandomValidBpmnId();

    BpmnEventTypeScenario(
        final String name, final BpmnElementType elementType, final BpmnEventType eventType) {
      this.name = name;
      this.elementType = elementType;
      this.eventType = eventType;
    }

    abstract BpmnModelInstance modelInstance();

    void executeInstance() {
      ENGINE.processInstance().ofBpmnProcessId(processId).create();
    }

    long executeInstance(final Map<String, Object> variables) {
      return ENGINE
          .processInstance()
          .ofBpmnProcessId(processId())
          .withVariables(variables)
          .create();
    }

    String processId() {
      return processId;
    }

    String elementId() {
      return elementId;
    }

    String messageName() {
      return messageName;
    }

    String jobType() {
      return jobType;
    }

    String errorCode() {
      return errorCode;
    }

    String escalationCode() {
      return escalationCode;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
