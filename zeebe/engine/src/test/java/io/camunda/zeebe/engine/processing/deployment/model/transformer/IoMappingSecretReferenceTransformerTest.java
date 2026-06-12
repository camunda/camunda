/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class IoMappingSecretReferenceTransformerTest {

  private static final String TASK_ID = "service-task";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  private ExecutableJobWorkerTask transformServiceTask(
      final Consumer<ServiceTaskBuilder> modifier) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask(TASK_ID, b -> modifier.accept(b.zeebeJobType("test")))
            .done();
    final List<ExecutableProcess> processes = transformer.transformDefinitions(model);
    return processes.get(0).getElementById(TASK_ID, ExecutableJobWorkerTask.class);
  }

  @Test
  void shouldExtractSecretReferenceFromInputMapping() {
    // when
    final var task =
        transformServiceTask(
            b -> b.zeebeInputExpression("camunda.secrets.TOKEN", "authentication.token"));

    // then
    assertThat(task.getInputSecretReferences())
        .containsOnly(entry("/authentication/token", Set.of("camunda.secrets.TOKEN")));
  }

  @Test
  void shouldExtractEmbeddedSecretReference() {
    // when — a static string with the reference embedded, e.g. "Bearer camunda.secrets.MY_TOKEN"
    final var task =
        transformServiceTask(
            b -> b.zeebeInput("Bearer camunda.secrets.MY_TOKEN", "authentication.header"));

    // then
    assertThat(task.getInputSecretReferences())
        .containsOnly(entry("/authentication/header", Set.of("camunda.secrets.MY_TOKEN")));
  }

  @Test
  void shouldExtractMultipleReferencesFromSingleMapping() {
    // when
    final var task =
        transformServiceTask(
            b -> b.zeebeInputExpression("camunda.secrets.A + camunda.secrets.B", "combined"));

    // then
    assertThat(task.getInputSecretReferences()).containsOnlyKeys("/combined");
    assertThat(task.getInputSecretReferences().get("/combined"))
        .containsExactlyInAnyOrder("camunda.secrets.A", "camunda.secrets.B");
  }

  @Test
  void shouldIndexReferencesByVariablePath() {
    // when — two mappings building the same top-level variable at different nested paths
    final var task =
        transformServiceTask(
            b ->
                b.zeebeInputExpression("camunda.secrets.X", "auth.token")
                    .zeebeInputExpression("camunda.secrets.Y", "auth.url"));

    // then
    assertThat(task.getInputSecretReferences())
        .containsOnly(
            entry("/auth/token", Set.of("camunda.secrets.X")),
            entry("/auth/url", Set.of("camunda.secrets.Y")));
  }

  @Test
  void shouldNotExtractWhenNoSecretReference() {
    // when
    final var task = transformServiceTask(b -> b.zeebeInputExpression("someVariable", "target"));

    // then
    assertThat(task.getInputSecretReferences()).isEmpty();
  }

  @Test
  void shouldHaveNoSecretReferencesWithoutInputMappings() {
    // when
    final var task = transformServiceTask(b -> {});

    // then
    assertThat(task.getInputSecretReferences()).isEmpty();
  }
}
