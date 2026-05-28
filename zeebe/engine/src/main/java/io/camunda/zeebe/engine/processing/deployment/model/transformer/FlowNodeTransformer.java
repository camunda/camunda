/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.ExecutionListenerTransformer;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class FlowNodeTransformer implements ModelElementTransformer<FlowNode> {

  /** Matches a {@code camunda.secrets.<name>} reference anywhere within a mapping source. */
  private static final Pattern SECRET_REFERENCE =
      Pattern.compile("camunda\\.secrets\\.[A-Za-z0-9_-]+");

  private final VariableMappingTransformer variableMappingTransformer =
      new VariableMappingTransformer();

  private final ExecutionListenerTransformer executionListenerTransformer =
      new ExecutionListenerTransformer();

  @Override
  public Class<FlowNode> getType() {
    return FlowNode.class;
  }

  @Override
  public void transform(final FlowNode flowNode, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableFlowNode element =
        process.getElementById(flowNode.getId(), ExecutableFlowNode.class);
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

    setParentReference(flowNode, process, element);
    transformIoMappings(flowNode, element, expressionLanguage);
    transformExecutionListeners(flowNode, element, expressionLanguage);
  }

  private void setParentReference(
      final FlowNode flowNode, final ExecutableProcess process, final ExecutableFlowNode element) {

    final var parentElement = flowNode.getParentElement();
    Optional.ofNullable(parentElement.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID))
        .map(BufferUtil::wrapString)
        .map(process::getElementById)
        .ifPresent(
            parent -> {
              element.setFlowScope(parent);

              if (parent instanceof final ExecutableFlowElementContainer container) {
                container.addChildElement(element);
              }
            });
  }

  private void transformIoMappings(
      final FlowNode element,
      final ExecutableFlowNode flowNode,
      final ExpressionLanguage expressionLanguage) {

    final var ioMapping =
        Optional.ofNullable(element.getSingleExtensionElement(ZeebeIoMapping.class));

    ioMapping
        .map(ZeebeIoMapping::getInputs)
        .filter(mappings -> !mappings.isEmpty())
        .map(
            mappings ->
                variableMappingTransformer.transformInputMappings(mappings, expressionLanguage))
        .ifPresent(flowNode::setInputMappings);

    ioMapping
        .map(ZeebeIoMapping::getInputs)
        .map(FlowNodeTransformer::extractInputSecretReferences)
        .filter(refs -> !refs.isEmpty())
        .ifPresent(flowNode::setInputSecretReferences);

    ioMapping
        .map(ZeebeIoMapping::getOutputs)
        .filter(mappings -> !mappings.isEmpty())
        .map(
            mappings ->
                variableMappingTransformer.transformOutputMappings(mappings, expressionLanguage))
        .ifPresent(flowNode::setOutputMappings);
  }

  /**
   * Scans each input mapping source for {@code camunda.secrets.*} references and indexes them by
   * the JSON-pointer path of the variable they produce. The path is derived from the mapping
   * target: {@code authentication.token} becomes {@code /authentication/token}. Only statically
   * modeled references are captured — this is what makes resolution safe against injection via
   * untrusted process variables.
   */
  private static Map<String, Set<String>> extractInputSecretReferences(
      final Collection<ZeebeInput> inputs) {
    final Map<String, Set<String>> secretReferences = new HashMap<>();
    for (final ZeebeInput input : inputs) {
      final String source = input.getSource();
      final String target = input.getTarget();
      if (source == null || target == null) {
        continue;
      }
      final Set<String> references =
          SECRET_REFERENCE
              .matcher(source)
              .results()
              .map(MatchResult::group)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      if (references.isEmpty()) {
        continue;
      }
      final String path = "/" + target.replace('.', '/');
      secretReferences.computeIfAbsent(path, k -> new LinkedHashSet<>()).addAll(references);
    }
    return secretReferences;
  }

  private void transformExecutionListeners(
      final FlowNode element,
      final ExecutableFlowNode flowNode,
      final ExpressionLanguage expressionLanguage) {

    Optional.ofNullable(element.getSingleExtensionElement(ZeebeExecutionListeners.class))
        .ifPresent(
            listeners ->
                executionListenerTransformer.transform(
                    element, flowNode, listeners.getExecutionListeners(), expressionLanguage));
  }
}
