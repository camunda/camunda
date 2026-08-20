/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.isAdHocRelatedProcess;
import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.isAdHocSubProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProcessInstanceMigrationPreconditions} focusing on Ad-Hoc Sub-Process
 * validation.
 *
 * <p>These are pure unit tests: the BPMN model is compiled directly with a {@link BpmnTransformer},
 * so they never start the engine or read its live ZeebeDb state.
 */
final class ProcessInstanceMigrationPreconditionsTest {

  private static final String PROCESS_ID = "process";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  // ==================== isAdHocSubProcess Tests ====================

  @Test
  void shouldReturnTrueForAdHocSubProcessElementType() {
    // when/then
    assertThat(isAdHocSubProcess(BpmnElementType.AD_HOC_SUB_PROCESS)).isTrue();
  }

  @Test
  void shouldReturnFalseForNonAdHocSubProcessElementType() {
    // when/then
    assertThat(isAdHocSubProcess(BpmnElementType.SERVICE_TASK)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.SUB_PROCESS)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.PROCESS)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.USER_TASK)).isFalse();
    assertThat(isAdHocSubProcess(BpmnElementType.CALL_ACTIVITY)).isFalse();
  }

  // ==================== isAdHocRelatedProcess Tests ====================

  @Test
  void shouldReturnTrueForAdHocSubProcess() {
    // given
    final DeployedProcess deployedProcess =
        deployedProcessOf(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .adHocSubProcess(
                    "adHocSubProcess",
                    ahsp -> ahsp.serviceTask("task", t -> t.zeebeJobType("task")))
                .endEvent()
                .done());

    // when/then
    assertThat(isAdHocRelatedProcess(deployedProcess, "adHocSubProcess")).isTrue();
  }

  @Test
  void shouldReturnFalseForNonAdHocSubProcess() {
    // given
    final DeployedProcess deployedProcess =
        deployedProcessOf(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done());

    // when/then
    assertThat(isAdHocRelatedProcess(deployedProcess, "task")).isFalse();
  }

  @Test
  void shouldReturnTrueForMultiInstanceAdHocSubProcess() {
    // given
    final DeployedProcess deployedProcess =
        deployedProcessOf(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .adHocSubProcess(
                    "adHocSubProcess",
                    ahsp -> ahsp.serviceTask("task", t -> t.zeebeJobType("task")))
                .multiInstance(
                    mi -> mi.zeebeInputCollectionExpression("[1,2,3]").zeebeInputElement("item"))
                .endEvent()
                .done());

    // when/then - should return true for the multi-instance body wrapping ad-hoc subprocess
    assertThat(isAdHocRelatedProcess(deployedProcess, "adHocSubProcess")).isTrue();
  }

  @Test
  void shouldReturnFalseForMultiInstanceNonAdHocSubProcess() {
    // given
    final DeployedProcess deployedProcess =
        deployedProcessOf(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .multiInstance(
                    mi -> mi.zeebeInputCollectionExpression("[1,2,3]").zeebeInputElement("item"))
                .endEvent()
                .done());

    // when/then - should return false for the multi-instance body wrapping non ad-hoc subprocess
    assertThat(isAdHocRelatedProcess(deployedProcess, "task")).isFalse();
  }

  /**
   * Compiles the BPMN model into an {@link ExecutableProcess} and wraps it in a {@link
   * DeployedProcess}. {@code isAdHocRelatedProcess} only reads the executable process, so the
   * accompanying {@link PersistedProcess} metadata is left empty.
   */
  private DeployedProcess deployedProcessOf(final BpmnModelInstance model) {
    final ExecutableProcess process = transformer.transformDefinitions(model).getFirst();
    return new DeployedProcess(process, new PersistedProcess());
  }
}
