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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.*;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Enclosed.class)
public class DuplicateExtensionElementDeploymentRejectionTest {

  public static class ProcessLevelTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
    @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

    @Test
    public void shouldRejectProcessWithDuplicateVersionTags() {
      final BpmnModelInstance model =
          Bpmn.createExecutableProcess("p1")
              .addExtensionElement(ZeebeVersionTag.class, vt -> {})
              .addExtensionElement(ZeebeVersionTag.class, vt -> {})
              .startEvent()
              .endEvent()
              .done();

      final var rejected = ENGINE.deployment().withXmlResource(model).expectRejection().deploy();

      assertThat(rejected.getRejectionReason())
          .contains(
              "Must have exactly one 'zeebe:"
                  + ZeebeConstants.ELEMENT_VERSION_TAG
                  + "' extension element when exists.");
    }
  }

  @RunWith(Parameterized.class)
  public static class DuplicateExtensionElementsParameterizedTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();
    private final ElementWithDuplicateExtensions adder;
    private final String elementId;
    private final String extensionName;

    public DuplicateExtensionElementsParameterizedTest(
        final ElementWithDuplicateExtensions adder,
        final String elementId,
        final String extensionName) {
      this.adder = adder;
      this.elementId = elementId;
      this.extensionName = extensionName;
    }

    @Parameterized.Parameters(name = "{1} → zeebe:{2}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            // -- FLOW-NODE (generic) ------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      // attach to the startEvent itself, to cover the "FlowNode" validators
                      b.manualTask("manualTask1")
                          .addExtensionElement(ZeebeIoMapping.class, t -> {})
                          .addExtensionElement(ZeebeIoMapping.class, t -> {})),
              "manualTask1",
              ZeebeConstants.ELEMENT_IO_MAPPING
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.manualTask("manualTask2")
                          .addExtensionElement(ZeebeExecutionListeners.class, t -> {})
                          .addExtensionElement(ZeebeExecutionListeners.class, t -> {})),
              "manualTask2",
              ZeebeConstants.ELEMENT_EXECUTION_LISTENERS
            },

            // -- SERVICE TASK ------------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.serviceTask(
                          "serviceTaskHeaders",
                          st ->
                              st.addExtensionElement(ZeebeTaskHeaders.class, t -> {})
                                  .addExtensionElement(ZeebeTaskHeaders.class, t -> {}))),
              "serviceTaskHeaders",
              ZeebeConstants.ELEMENT_TASK_HEADERS
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.serviceTask(
                          "serviceTaskLinked",
                          st ->
                              st.addExtensionElement(ZeebeLinkedResources.class, t -> {})
                                  .addExtensionElement(ZeebeLinkedResources.class, t -> {}))),
              "serviceTaskLinked",
              ZeebeConstants.ELEMENT_LINKED_RESOURCES
            },

            // -- SEND TASK ---------------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.sendTask(
                          "sendTaskHeaders",
                          st ->
                              st.addExtensionElement(ZeebeTaskHeaders.class, t -> {})
                                  .addExtensionElement(ZeebeTaskHeaders.class, t -> {}))),
              "sendTaskHeaders",
              ZeebeConstants.ELEMENT_TASK_HEADERS
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.sendTask(
                          "sendTaskLinked",
                          st ->
                              st.addExtensionElement(ZeebeLinkedResources.class, t -> {})
                                  .addExtensionElement(ZeebeLinkedResources.class, t -> {}))),
              "sendTaskLinked",
              ZeebeConstants.ELEMENT_LINKED_RESOURCES
            },

            // -- USER TASK ---------------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskMain",
                          ut ->
                              ut.addExtensionElement(ZeebeUserTask.class, t -> {})
                                  .addExtensionElement(ZeebeUserTask.class, t -> {}))),
              "userTaskMain",
              ZeebeConstants.ELEMENT_USER_TASK
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskAssign",
                          ut ->
                              ut.addExtensionElement(ZeebeAssignmentDefinition.class, t -> {})
                                  .addExtensionElement(ZeebeAssignmentDefinition.class, t -> {}))),
              "userTaskAssign",
              ZeebeConstants.ELEMENT_ASSIGNMENT_DEFINITION
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskForm",
                          ut ->
                              ut.addExtensionElement(ZeebeFormDefinition.class, t -> {})
                                  .addExtensionElement(ZeebeFormDefinition.class, t -> {}))),
              "userTaskForm",
              ZeebeConstants.ELEMENT_FORM_DEFINITION
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskHeaders",
                          ut ->
                              ut.addExtensionElement(ZeebeTaskHeaders.class, t -> {})
                                  .addExtensionElement(ZeebeTaskHeaders.class, t -> {}))),
              "userTaskHeaders",
              ZeebeConstants.ELEMENT_TASK_HEADERS
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskSchedule",
                          ut ->
                              ut.addExtensionElement(ZeebeTaskSchedule.class, t -> {})
                                  .addExtensionElement(ZeebeTaskSchedule.class, t -> {}))),
              "userTaskSchedule",
              ZeebeConstants.ELEMENT_SCHEDULE_DEFINITION
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskPriority",
                          ut ->
                              ut.addExtensionElement(ZeebePriorityDefinition.class, t -> {})
                                  .addExtensionElement(ZeebePriorityDefinition.class, t -> {}))),
              "userTaskPriority",
              ZeebeConstants.ELEMENT_PRIORITY_DEFINITION
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.userTask(
                          "userTaskListeners",
                          ut ->
                              ut.addExtensionElement(ZeebeTaskListeners.class, t -> {})
                                  .addExtensionElement(ZeebeTaskListeners.class, t -> {}))),
              "userTaskListeners",
              ZeebeConstants.ELEMENT_TASK_LISTENERS
            },

            // -- BUSINESS RULE TASK ------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.businessRuleTask(
                          "brtDef",
                          brt ->
                              brt.addExtensionElement(ZeebeTaskDefinition.class, t -> {})
                                  .addExtensionElement(ZeebeTaskDefinition.class, t -> {}))),
              "brtDef",
              ZeebeConstants.ELEMENT_TASK_DEFINITION
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.businessRuleTask(
                          "brtHeaders",
                          brt ->
                              brt.addExtensionElement(ZeebeTaskHeaders.class, t -> {})
                                  .addExtensionElement(ZeebeTaskHeaders.class, t -> {}))),
              "brtHeaders",
              ZeebeConstants.ELEMENT_TASK_HEADERS
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.businessRuleTask(
                          "brtDecision",
                          brt ->
                              brt.addExtensionElement(ZeebeCalledDecision.class, t -> {})
                                  .addExtensionElement(ZeebeCalledDecision.class, t -> {}))),
              "brtDecision",
              ZeebeConstants.ELEMENT_CALLED_DECISION
            },

            // -- SCRIPT TASK -------------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.scriptTask(
                          "scriptMain",
                          st ->
                              st.addExtensionElement(ZeebeScript.class, t -> {})
                                  .addExtensionElement(ZeebeScript.class, t -> {}))),
              "scriptMain",
              ZeebeConstants.ELEMENT_SCRIPT
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.scriptTask(
                          "scriptDef",
                          st ->
                              st.addExtensionElement(ZeebeTaskDefinition.class, t -> {})
                                  .addExtensionElement(ZeebeTaskDefinition.class, t -> {}))),
              "scriptDef",
              ZeebeConstants.ELEMENT_TASK_DEFINITION
            },
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.scriptTask(
                          "scriptHeaders",
                          st ->
                              st.addExtensionElement(ZeebeTaskHeaders.class, t -> {})
                                  .addExtensionElement(ZeebeTaskHeaders.class, t -> {}))),
              "scriptHeaders",
              ZeebeConstants.ELEMENT_TASK_HEADERS
            },

            // -- AD-HOC SUB PROCESS ------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.adHocSubProcess("adHoc1", AbstractFlowNodeBuilder::manualTask)
                          .addExtensionElement(ZeebeAdHoc.class, t -> {})
                          .addExtensionElement(ZeebeAdHoc.class, t -> {})),
              "adHoc1",
              ZeebeConstants.ELEMENT_AD_HOC
            },

            // -- END EVENT ---------------------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.endEvent("endEvent1")
                          .addExtensionElement(ZeebeTaskDefinition.class, t -> {})
                          .addExtensionElement(ZeebeTaskDefinition.class, t -> {})),
              "endEvent1",
              ZeebeConstants.ELEMENT_TASK_DEFINITION
            },

            // -- INTERMEDIATE THROW EVENT ------------------------------
            {
              (ElementWithDuplicateExtensions)
                  (b ->
                      b.intermediateThrowEvent("interThrow1")
                          .addExtensionElement(ZeebeTaskDefinition.class, t -> {})
                          .addExtensionElement(ZeebeTaskDefinition.class, t -> {})),
              "interThrow1",
              ZeebeConstants.ELEMENT_TASK_DEFINITION
            },
          });
    }

    @Test
    public void shouldRejectDeploymentOnDuplicatedExtensions() {
      final BpmnModelInstance model = betweenStartAndEnd("proc-" + elementId, adder);

      final var rejected = ENGINE.deployment().withXmlResource(model).expectRejection().deploy();

      Assertions.assertThat(rejected)
          .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
          .hasRecordType(RecordType.COMMAND_REJECTION)
          .hasIntent(DeploymentIntent.CREATE)
          .hasRejectionType(RejectionType.INVALID_ARGUMENT);

      final String reason = rejected.getRejectionReason();
      assertThat(reason)
          .contains("Element: " + elementId)
          .contains(
              "ERROR: Must have exactly one 'zeebe:"
                  + extensionName
                  + "' extension element when exists.");
    }

    /** Wraps duplicate‐extension insertion between startEvent() → ... → endEvent().done() */
    private static BpmnModelInstance betweenStartAndEnd(
        final String processId, final ElementWithDuplicateExtensions between) {

      final AbstractFlowNodeBuilder<?, ?> start =
          Bpmn.createExecutableProcess(processId).startEvent();

      final AbstractFlowNodeBuilder<?, ?> afterTestElement = between.apply(start);

      return afterTestElement.endEvent().done();
    }

    /**
     * Given a builder sitting after startEvent(), insert exactly one BPMN element (userTask,
     * serviceTask, etc.) with *two* identical Zeebe extensions.
     */
    @FunctionalInterface
    interface ElementWithDuplicateExtensions {
      AbstractFlowNodeBuilder<?, ?> apply(AbstractFlowNodeBuilder<?, ?> builder);
    }
  }
}
