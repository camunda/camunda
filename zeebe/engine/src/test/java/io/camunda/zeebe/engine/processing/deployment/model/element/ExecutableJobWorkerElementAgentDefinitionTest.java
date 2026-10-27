/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExecutableJobWorkerElementAgentDefinitionTest {

  private static Stream<Arguments> elements() {
    return Stream.of(
        Arguments.of(Named.of("service task", new ExecutableJobWorkerTask("serviceTask"))),
        Arguments.of(
            Named.of("ad-hoc sub-process", new ExecutableAdHocSubProcess("adHocSubProcess"))));
  }

  @ParameterizedTest(name = "{0} defaults to AgentDefinitionType.UNSPECIFIED")
  @MethodSource("elements")
  void shouldDefaultToUnspecified(final ExecutableJobWorkerElement element) {
    // when / then
    assertThat(element.getAgentDefinitionType())
        .describedAs("Should default to AgentDefinitionType.UNSPECIFIED")
        .isEqualTo(AgentDefinitionType.UNSPECIFIED);
    assertThat(element.isAgentDefinition())
        .describedAs("Should not be an agent definition by default")
        .isFalse();
  }

  @ParameterizedTest(name = "{0} reports an agent definition once its type is set")
  @MethodSource("elements")
  void shouldReportAgentDefinitionOnceTypeIsSet(final ExecutableJobWorkerElement element) {
    // given
    element.setAgentDefinitionType(AgentDefinitionType.EXTERNAL_AGENT);

    // when / then
    assertThat(element.getAgentDefinitionType())
        .describedAs("Should return the AgentDefinitionType set via setAgentDefinitionType()")
        .isEqualTo(AgentDefinitionType.EXTERNAL_AGENT);
    assertThat(element.isAgentDefinition())
        .describedAs("Should be an agent definition once its type is set")
        .isTrue();
  }

  @ParameterizedTest(
      name = "{0} does not report an agent definition when explicitly set to UNSPECIFIED")
  @MethodSource("elements")
  void shouldNotReportAgentDefinitionWhenExplicitlySetToUnspecified(
      final ExecutableJobWorkerElement element) {
    // given
    element.setAgentDefinitionType(AgentDefinitionType.UNSPECIFIED);

    // when / then
    assertThat(element.isAgentDefinition())
        .describedAs("Should not be an agent definition when explicitly set to UNSPECIFIED")
        .isFalse();
  }
}
