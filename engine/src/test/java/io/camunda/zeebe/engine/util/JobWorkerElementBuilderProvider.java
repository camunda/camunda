/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

/**
 * An argument provider for {@link JobWorkerElementBuilder}. It can be used for tests that verify
 * the behavior of elements that are based on jobs and should be processed by job workers. For
 * example, service tasks.
 */
public final class JobWorkerElementBuilderProvider implements ArgumentsProvider {

  @Override
  public Stream<? extends Arguments> provideArguments(final ExtensionContext extensionContext)
      throws Exception {
    return builders().map(Arguments::of);
  }

  public static Stream<JobWorkerElementBuilder> builders() {
    return Stream.of(
        JobWorkerElementBuilder.of(
            BpmnElementType.SERVICE_TASK, AbstractFlowNodeBuilder::serviceTask),
        JobWorkerElementBuilder.of(
            BpmnElementType.BUSINESS_RULE_TASK, AbstractFlowNodeBuilder::businessRuleTask),
        JobWorkerElementBuilder.of(
            BpmnElementType.SCRIPT_TASK, AbstractFlowNodeBuilder::scriptTask),
        JobWorkerElementBuilder.of(BpmnElementType.SEND_TASK, AbstractFlowNodeBuilder::sendTask),
        JobWorkerElementBuilder.of(
            BpmnElementType.END_EVENT,
            process ->
                process.endEvent("message", AbstractThrowEventBuilder::messageEventDefinition)),
        JobWorkerElementBuilder.of(
            BpmnElementType.INTERMEDIATE_THROW_EVENT,
            process ->
                process.intermediateThrowEvent(
                    "message", AbstractThrowEventBuilder::messageEventDefinition)));
  }

  public static Collection<Object[]> buildersAsParameters() {
    return builders().map(builder -> new Object[] {builder}).collect(Collectors.toList());
  }
}
