/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCase;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCase.ExecutableStage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCase;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeStage;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.bpm.model.xml.impl.ModelInstanceImpl;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProcessTransformerV2 implements ModelElementTransformer<Process> {

  private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();

  // v1 is frozen, so delegating to it keeps v2 in step with what v1 produces
  private final ProcessTransformer processTransformer = new ProcessTransformer();

  @Override
  public Class<Process> getType() {
    return Process.class;
  }

  @Override
  public void transform(final Process element, final TransformContext context) {
    processTransformer.transform(element, context);

    final ExecutableProcess process = context.getCurrentProcess();
    process.setName(
        Optional.ofNullable(element.getName()).map(BufferUtil::wrapString).orElse(EMPTY_BUFFER));
    transformCase(element, process);
  }

  /**
   * Reads the case marker and the stages from the model: the PROCESS slot runs before the child
   * elements are transformed, so there are no executable elements to look at yet.
   */
  private void transformCase(final Process element, final ExecutableProcess process) {
    Optional.ofNullable(element.getSingleExtensionElement(ZeebeCase.class))
        .ifPresent(
            zeebeCase ->
                process.setCaseDefinition(
                    new ExecutableCase(
                        BufferUtil.wrapString(zeebeCase.getTitle()), stages(element))));
  }

  private List<ExecutableStage> stages(final Process element) {
    // getChildElementsByType groups the children by their concrete type; the DOM keeps the order
    // of the document
    return ModelUtil.getModelElementCollection(
            element.getDomElement().getChildElements(),
            (ModelInstanceImpl) element.getModelInstance())
        .stream()
        .filter(SubProcess.class::isInstance)
        .map(SubProcess.class::cast)
        .filter(subProcess -> subProcess.getSingleExtensionElement(ZeebeStage.class) != null)
        .map(
            subProcess ->
                new ExecutableStage(
                    subProcess.getId(), Objects.requireNonNullElse(subProcess.getName(), "")))
        .toList();
  }
}
