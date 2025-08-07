/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocActivityMetadata;
import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocActivityMetadata.AdHocActivityParameter;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class AdHocSubProcessElementsVariableTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String AD_HOC_SUB_PROCESS_ELEMENTS_VARIABLE = "adHocSubProcessElements";

  private static final AdHocSubProcessElementTestFixture SIMPLE_TASK =
      testFixture(
          ahsp -> {
            final var taskBuilder = ahsp.task("Simple_Task");
            taskBuilder.name("Simple Task");
            taskBuilder.documentation("The Simple Task documentation");
            taskBuilder.zeebeProperty("someProperty", "someValue");
          },
          new AdHocActivityMetadata(
              "Simple_Task",
              "Simple Task",
              "The Simple Task documentation",
              Map.of("someProperty", "someValue"),
              null));

  private static final AdHocSubProcessElementTestFixture TASK_WITH_PROPERTIES =
      testFixture(
          ahsp -> {
            final var taskBuilder = ahsp.task("Task_With_Properties");
            taskBuilder.name("Task With Properties");
            taskBuilder.documentation("The task with properties documentation");
            taskBuilder
                .zeebeProperty("io.camunda.test.property1", "value1")
                .zeebeProperty("io.camunda.test.property2", "value2")
                .zeebeProperty("io.camunda.test.property3", "")
                .zeebeProperty("io.camunda.test.property4", "   ")
                .zeebeProperty("io.camunda.test.property5", null);
          },
          new AdHocActivityMetadata(
              "Task_With_Properties",
              "Task With Properties",
              "The task with properties documentation",
              new LinkedHashMap<>() {
                {
                  put("io.camunda.test.property1", "value1");
                  put("io.camunda.test.property2", "value2");
                  put("io.camunda.test.property3", null);
                  put("io.camunda.test.property4", "   ");
                  put("io.camunda.test.property5", null);
                }
              },
              null));

  private static final AdHocSubProcessElementTestFixture SERVICE_TASK =
      testFixture(
          ahsp ->
              ahsp.serviceTask(
                  "Service_Task",
                  serviceTask -> {
                    serviceTask.name("Service Task");
                    serviceTask
                        .zeebeJobType("serviceTaskJobType")
                        .zeebeInput("=fromAi(toolCall.a, \"Input A\", \"number\")", "inputA")
                        .zeebeInput("=fromAi(b, \"Input B\", \"number\")", "inputB")
                        .zeebeInput(
                            "=string(fromAi(toolCall.c, \"Input C\", \"number\"))", "inputC")
                        .zeebeInput("=123456", "inputD"); // not a parameter, just a static value
                  }),
          new AdHocActivityMetadata(
              "Service_Task",
              "Service Task",
              null,
              null,
              List.of(
                  new AdHocActivityParameter("toolCall.a", "Input A", "number", null, null),
                  new AdHocActivityParameter("b", "Input B", "number", null, null),
                  new AdHocActivityParameter("toolCall.c", "Input C", "number", null, null))));

  private static final AdHocSubProcessElementTestFixture USER_TASK =
      testFixture(
          ahsp ->
              ahsp.userTask(
                  "User_Task",
                  userTask -> {
                    userTask.name("User Task");
                    userTask.documentation("A user task with documentation");
                    userTask
                        .zeebeUserTask()
                        .zeebeInput(
                            "=fromAi(toolCall.userTaskInput, \"Input for the user task\")",
                            "userTaskInput");
                  }),
          new AdHocActivityMetadata(
              "User_Task",
              "User Task",
              "A user task with documentation",
              null,
              List.of(
                  new AdHocActivityParameter(
                      "toolCall.userTaskInput", "Input for the user task", null, null, null))));

  private static final AdHocSubProcessElementTestFixture SCRIPT_TASK =
      testFixture(
          ahsp ->
              ahsp.scriptTask(
                      "Script_Task",
                      scriptTask -> {
                        scriptTask.name("Script Task");
                        scriptTask.documentation("A script task with documentation");
                        scriptTask
                            .zeebeResultVariable("scriptTaskResult")
                            .zeebeExpression("inputVariable")
                            .zeebeProperty("io.camunda.test.property1", "value1")
                            .zeebeInput(
                                "=fromAi(toolCall.inputVariable, \"Input variable description\")",
                                "inputVariable");
                      })
                  .boundaryEvent(
                      "Handle_Errors",
                      boundaryEvent -> {
                        boundaryEvent.error().documentation("Handles errors from script task");
                      })
                  .manualTask("Handle_Script_Task_Error"),
          new AdHocActivityMetadata(
              "Script_Task",
              "Script Task",
              "A script task with documentation",
              Map.of("io.camunda.test.property1", "value1"),
              List.of(
                  new AdHocActivityParameter(
                      "toolCall.inputVariable", "Input variable description", null, null, null))));

  private static final AdHocSubProcessElementTestFixture TASK_WITH_FOLLOW_UP =
      testFixture(
          ahsp -> {
            final var taskBuilder = ahsp.task("A_Task_With_Follow_Up");
            taskBuilder.name("A Task With Follow-Up");

            final var followUpTaskBuilder = taskBuilder.task("Follow_Up_Task");
            followUpTaskBuilder.name("Follow-Up Task");
          },
          new AdHocActivityMetadata(
              "A_Task_With_Follow_Up", "A Task With Follow-Up", null, null, null));

  private static final AdHocSubProcessElementTestFixture INTERMEDIATE_THROW_EVENT_WITH_FOLLOW_UP =
      testFixture(
          ahsp -> {
            final var eventBuilder =
                ahsp.intermediateThrowEvent(
                    "An_Event",
                    event -> {
                      event.name("An event!");
                      event.documentation("The event documentation");
                      event.zeebeProperty("eventProperty", "eventPropertyName");
                    });

            final var followUpTaskBuilder = eventBuilder.task("Event_Follow_Up_Task");
            followUpTaskBuilder.name("Event Follow-Up Task");
          },
          new AdHocActivityMetadata(
              "An_Event",
              "An event!",
              "The event documentation",
              Map.of("eventProperty", "eventPropertyName"),
              null));

  private static final AdHocSubProcessElementTestFixture COMPLEX_TOOL =
      testFixture(
          ahsp ->
              ahsp.scriptTask(
                  "A_Complex_Tool",
                  scriptTask -> {
                    scriptTask.name("A complex tool");
                    scriptTask.documentation("A complex tool with nested parameters");
                    scriptTask
                        .zeebeResultVariable("complexToolResult")
                        .zeebeExpression("anArrayVariable")
                        .zeebeProperty("complexToolProperty", "complexValue")
                        .zeebeInput(
                            """
                            =fromAi(toolCall.aSimpleValue, "A simple value")
                            """,
                            "aSimpleValue")
                        .zeebeInput(
                            """
                            =fromAi(toolCall.anEnumValue, "An enum value", "string", { enum: ["A", "B", "C"] })
                            """,
                            "anEnumValue")
                        .zeebeInput(
                            """
                            =fromAi(toolCall.anArrayValue, "An array value", "array", {
                              items: {
                                type: "string",
                                enum: ["foo", "bar", "baz"]
                              }
                            })
                            """,
                            "anArrayValue")
                        .zeebeInput(
                            """
                            ="https://example.com/" + fromAi(toolCall.urlPath, "The URL path to use", "string")
                            """,
                            "aCombinedValue")
                        .zeebeInput(
                            """
                            ={
                              comment: "Multiple params, positional & named, simple & complex",
                              foo: [
                                fromAi(firstValue),
                                string(fromAi(toolCall.secondValue, "The second value",  "integer"))
                              ],
                              bar: {
                                baz: fromAi(description: "The third value to add", value: toolCall.thirdValue),
                                qux: fromAi(toolCall.fourthValue, "The fourth value to add", "array", {
                                  "items": {
                                    "type": "string",
                                    "enum": ["foo", "bar", "baz"]
                                  }
                                })
                              }
                            }
                            """,
                            "multipleParametersInDifferentFormats");
                  }),
          new AdHocActivityMetadata(
              "A_Complex_Tool",
              "A complex tool",
              "A complex tool with nested parameters",
              Map.of("complexToolProperty", "complexValue"),
              List.of(
                  new AdHocActivityParameter(
                      "toolCall.aSimpleValue", "A simple value", null, null, null),
                  new AdHocActivityParameter(
                      "toolCall.anEnumValue",
                      "An enum value",
                      "string",
                      Map.of("enum", List.of("A", "B", "C")),
                      null),
                  new AdHocActivityParameter(
                      "toolCall.anArrayValue",
                      "An array value",
                      "array",
                      Map.of(
                          "items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                      null),
                  new AdHocActivityParameter(
                      "toolCall.urlPath", "The URL path to use", "string", null, null),
                  new AdHocActivityParameter("firstValue", null, null, null, null),
                  new AdHocActivityParameter(
                      "toolCall.secondValue", "The second value", "integer", null, null),
                  new AdHocActivityParameter(
                      "toolCall.thirdValue", "The third value to add", null, null, null),
                  new AdHocActivityParameter(
                      "toolCall.fourthValue",
                      "The fourth value to add",
                      "array",
                      Map.of(
                          "items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                      null))));

  private static final String EVENT_SUBPROCESS_ID = "Event_Subprocess";
  private static final String EVENT_SUBPROCESS_START_EVENT_ID = EVENT_SUBPROCESS_ID + "_Start";
  private static final AdHocSubProcessElementTestFixture EVENT_SUB_PROCESS =
      testFixture(
          ahsp ->
              ahsp.embeddedSubProcess()
                  .eventSubProcess(
                      EVENT_SUBPROCESS_ID,
                      eventSubProcess -> {
                        eventSubProcess.name("Event Subprocess");
                        eventSubProcess.documentation("A non-interrupting event subprocess");
                        eventSubProcess
                            .startEvent(EVENT_SUBPROCESS_START_EVENT_ID)
                            .message(m -> m.zeebeCorrelationKey("=testCorrelationKey"))
                            .interrupting(false)
                            .manualTask()
                            .endEvent();
                      }),
          null);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private final AdHocSubProcessElementsScenario scenario;

  public AdHocSubProcessElementsVariableTest(final AdHocSubProcessElementsScenario scenario) {
    this.scenario = scenario;
  }

  @Parameters(name = "{0}")
  public static Collection<AdHocSubProcessElementsScenario> scenarios() {
    return List.of(
        scenario("Simple Task", SIMPLE_TASK),
        scenario("Task With Properties", TASK_WITH_PROPERTIES),
        scenario("Service Task", SERVICE_TASK),
        scenario("User Task", USER_TASK),
        scenario("Script Task", SCRIPT_TASK),
        scenario("Task with Follow-Up", TASK_WITH_FOLLOW_UP),
        scenario(
            "Intermediate Throw Event with Follow-Up", INTERMEDIATE_THROW_EVENT_WITH_FOLLOW_UP),
        scenario("Complex Tool", COMPLEX_TOOL),
        scenario("Event Subprocess", EVENT_SUB_PROCESS),
        scenario("Simple Task + Event Subprocess", SIMPLE_TASK, EVENT_SUB_PROCESS),
        scenario(
            "Multiple elements",
            SIMPLE_TASK,
            SERVICE_TASK,
            USER_TASK,
            COMPLEX_TOOL,
            EVENT_SUB_PROCESS),
        scenario(
            "All supported elements",
            SIMPLE_TASK,
            TASK_WITH_PROPERTIES,
            SERVICE_TASK,
            USER_TASK,
            SCRIPT_TASK,
            TASK_WITH_FOLLOW_UP,
            INTERMEDIATE_THROW_EVENT_WITH_FOLLOW_UP,
            COMPLEX_TOOL,
            EVENT_SUB_PROCESS));
  }

  @Test
  public void shouldSetExpectedAdHocSubProcessElementsVariable() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, scenario::modify)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var adHocSubProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst()
            .getKey();

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filter(
                    v ->
                        v.getIntent() == VariableIntent.CREATED
                            && v.getValue().getName().equals(AD_HOC_SUB_PROCESS_ELEMENTS_VARIABLE))
                .limit(1))
        .first()
        .describedAs(
            "Variable adHocSubProcessElements should be created as local variable in sub-process scope")
        .satisfies(
            variableRecord -> {
              assertThat(variableRecord.getValue()).hasScopeKey(adHocSubProcessKey);

              final List<AdHocActivityMetadata> adHocActivities =
                  OBJECT_MAPPER.readValue(
                      variableRecord.getValue().getValue(), new TypeReference<>() {});

              assertThat(adHocActivities)
                  .containsExactlyInAnyOrderElementsOf(scenario.expectedElements());

              // event subprocess elements should never be included
              assertThat(adHocActivities)
                  .extracting(AdHocActivityMetadata::elementId)
                  .doesNotContain(EVENT_SUBPROCESS_ID, EVENT_SUBPROCESS_START_EVENT_ID);
            });
  }

  private static AdHocSubProcessElementsScenario scenario(
      final String description, final AdHocSubProcessElementTestFixture... testFixtures) {
    return AdHocSubProcessElementsScenario.forTestFixtures(description, testFixtures);
  }

  private static AdHocSubProcessElementTestFixture testFixture(
      final Consumer<AdHocSubProcessBuilder> processModifier,
      final AdHocActivityMetadata metadataRepresentation) {
    return new AdHocSubProcessElementTestFixture(processModifier, metadataRepresentation);
  }

  public record AdHocSubProcessElementsScenario(
      String description,
      List<Consumer<AdHocSubProcessBuilder>> modifiers,
      List<AdHocActivityMetadata> expectedElements) {

    public void modify(final AdHocSubProcessBuilder adHocSubProcess) {
      modifiers.forEach(modifier -> modifier.accept(adHocSubProcess));
    }

    @Override
    public String toString() {
      return description;
    }

    static AdHocSubProcessElementsScenario forTestFixtures(
        final String description, final AdHocSubProcessElementTestFixture... testFixtures) {
      return new AdHocSubProcessElementsScenario(
          description,
          Arrays.stream(testFixtures)
              .map(AdHocSubProcessElementTestFixture::processModifier)
              .filter(Objects::nonNull)
              .toList(),
          Arrays.stream(testFixtures)
              .map(AdHocSubProcessElementTestFixture::expectedMetadata)
              .filter(Objects::nonNull)
              .toList());
    }
  }

  private record AdHocSubProcessElementTestFixture(
      Consumer<AdHocSubProcessBuilder> processModifier, AdHocActivityMetadata expectedMetadata) {}
}
