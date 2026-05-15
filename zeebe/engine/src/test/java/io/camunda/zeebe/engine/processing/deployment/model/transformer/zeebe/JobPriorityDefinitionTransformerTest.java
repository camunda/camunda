/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import org.camunda.feel.FeelEngineClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class JobPriorityDefinitionTransformerTest {

  private JobPriorityDefinitionTransformer transformer;
  private TransformContext context;
  private ExecutableProcess process;
  private ExpressionLanguage expressionLanguage;

  @BeforeEach
  void setUp() {
    transformer = new JobPriorityDefinitionTransformer();
    expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            (FeelEngineClock) () -> java.time.ZonedDateTime.now());
    process = new ExecutableProcess("process");
    context = new TransformContext();
    context.setExpressionLanguage(expressionLanguage);
    context.setCurrentProcess(process);
  }

  private static ExecutableJobWorkerTask elementWithJobWorkerProperties() {
    final var element = new ExecutableJobWorkerTask("serviceTask");
    element.setJobWorkerProperties(new JobWorkerProperties());
    return element;
  }

  @Test
  void shouldUseTaskLevelPriorityWhenPresent() {
    // given
    final var element = elementWithJobWorkerProperties();
    final var priorityDef = Mockito.mock(ZeebeJobPriorityDefinition.class);
    Mockito.when(priorityDef.getPriority()).thenReturn("42");

    // when
    transformer.transform(element, context, priorityDef);

    // then
    final Expression jobPriority = element.getJobWorkerProperties().getJobPriority();
    assertThat(jobPriority).isNotNull();
    assertThat(jobPriority.getExpression()).isEqualTo("42");
  }

  @Test
  void shouldFallBackToProcessDefaultWhenTaskLevelAbsent() {
    // given
    final var element = elementWithJobWorkerProperties();
    final Expression processDefault = expressionLanguage.parseExpression("=tier");
    process.setDefaultJobPriority(processDefault);

    // when
    transformer.transform(element, context, null);

    // then
    assertThat(element.getJobWorkerProperties().getJobPriority()).isSameAs(processDefault);
  }

  @Test
  void shouldPreferTaskLevelOverProcessDefault() {
    // given
    final var element = elementWithJobWorkerProperties();
    process.setDefaultJobPriority(expressionLanguage.parseExpression("10"));
    final var priorityDef = Mockito.mock(ZeebeJobPriorityDefinition.class);
    Mockito.when(priorityDef.getPriority()).thenReturn("99");

    // when
    transformer.transform(element, context, priorityDef);

    // then
    assertThat(element.getJobWorkerProperties().getJobPriority().getExpression()).isEqualTo("99");
  }

  @Test
  void shouldFallBackToZeroLiteralWhenNeitherPresent() {
    // given
    final var element = elementWithJobWorkerProperties();

    // when
    transformer.transform(element, context, null);

    // then
    final Expression jobPriority = element.getJobWorkerProperties().getJobPriority();
    assertThat(jobPriority).isNotNull();
    assertThat(jobPriority.getExpression()).isEqualTo("0");
  }

  @Test
  void shouldStoreFeelExpressionAsIsWithoutEvaluating() {
    // given
    final var element = elementWithJobWorkerProperties();
    final var priorityDef = Mockito.mock(ZeebeJobPriorityDefinition.class);
    Mockito.when(priorityDef.getPriority()).thenReturn("=customer.tier * 10");

    // when
    transformer.transform(element, context, priorityDef);

    // then
    final Expression jobPriority = element.getJobWorkerProperties().getJobPriority();
    assertThat(jobPriority).isNotNull();
    assertThat(jobPriority.isStatic()).isFalse();
  }

  @Test
  void shouldNotMaterialiseJobWorkerPropertiesForDirectExecutionTasks() {
    // given
    final var element = new ExecutableJobWorkerTask("scriptTask");
    final var priorityDef = Mockito.mock(ZeebeJobPriorityDefinition.class);
    Mockito.when(priorityDef.getPriority()).thenReturn("42");

    // when
    transformer.transform(element, context, priorityDef);

    // then
    assertThat(element.getJobWorkerProperties())
        .as(
            "should not create JobWorkerProperties for elements without a <zeebe:taskDefinition>"
                + " (would break straight-through-loop validation)")
        .isNull();
  }
}
