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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class FlowNodeSecretReferenceTest {

  private static final String TASK_ID = "task";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  @Test
  void shouldStoreSecretReferenceKeyedByJsonPointer() {
    // given
    final var task =
        transform(
            t ->
                t.zeebeInputExpression(
                    "\"Bearer \" + camunda.secrets.token", "tokens.externalSystemToken"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then - the dotted target path is stored as a JSON pointer
    assertThat(secretReferences)
        .containsExactly(
            entry("/tokens/externalSystemToken", Set.of(new SecretReference("token"))));
  }

  @Test
  void shouldStoreMultipleReferencesForSingleInputMapping() {
    // given
    final var task =
        transform(
            t ->
                t.zeebeInputExpression(
                    "\"Bearer \" + camunda.secrets.token + camunda.secrets.postfix",
                    "tokens.externalSystemToken"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences)
        .containsExactly(
            entry(
                "/tokens/externalSystemToken",
                Set.of(new SecretReference("token"), new SecretReference("postfix"))));
  }

  @Test
  void shouldStoreReferencesFromMultipleInputMappings() {
    // given
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.secrets.token", "auth.token")
                    .zeebeInputExpression("camunda.secrets.apiKey", "auth.key"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "/auth/token", Set.of(new SecretReference("token")),
                "/auth/key", Set.of(new SecretReference("apiKey"))));
  }

  @Test
  void shouldScopeJsonPointerToContextEntryOfReference() {
    // given - the source is a FEEL context; only one entry holds a secret reference
    final var task =
        transform(
            t ->
                t.zeebeInputExpression(
                    "{x1: \"camunda.secrets.x\", x2: camunda.secrets.x}", "foo"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then - the pointer targets the entry that references the secret, not the whole target
    assertThat(secretReferences)
        .containsExactly(entry("/foo/x2", Set.of(new SecretReference("x"))));
  }

  @Test
  void shouldNotStoreSecretReferenceUsedAsTarget() {
    // given - the reference-looking path is the target, the source holds no reference
    final var task = transform(t -> t.zeebeInputExpression("someVariable", "camunda.secrets.x"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences).isEmpty();
  }

  @Test
  void shouldStoreSecretReferencesByJsonPointerForNestedTargetPath() {
    // given - a nested (multi-segment) target path
    final var task =
        transform(t -> t.zeebeInputExpression("camunda.secrets.token", "auth.headers.token"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then - each dot becomes a pointer segment
    assertThat(secretReferences)
        .containsExactly(entry("/auth/headers/token", Set.of(new SecretReference("token"))));
  }

  @Test
  void shouldNotStoreSecretReferenceFromOutputMapping() {
    // given - a secret reference outside of an input mapping stays a literal
    final var task = transform(t -> t.zeebeOutputExpression("camunda.secrets.token", "result"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences).isEmpty();
  }

  @Test
  void shouldNotStoreSecretReferenceUsedAsStringLiteral() {
    // given - the reference is a string literal, not an expression
    final var task =
        transform(
            t -> t.zeebeInputExpression("\"camunda.secrets.token\"", "tokens.externalSystemToken"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences).isEmpty();
  }

  @Test
  void shouldNotStoreSecretReferenceFromStaticInputMapping() {
    // given - a static (non-expression) input mapping source is a literal
    final var task = transform(t -> t.zeebeInput("camunda.secrets.token", "tokens.token"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences).isEmpty();
  }

  @Test
  void shouldHaveEmptySecretReferencesWithoutInputMappings() {
    // given
    final var task = transform(t -> {});

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences).isEmpty();
  }

  @Test
  void shouldStoreOnlyEffectiveSecretWhenTargetIsOverridden() {
    // given - two input mappings with the same target; the later one overrides the earlier, so the
    // generated mapping expression keeps only the last source for that target
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.secrets.a", "x")
                    .zeebeInputExpression("camunda.secrets.b", "x"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then - only the effective (last) mapping's secret is stored, not the overridden one
    assertThat(secretReferences).containsExactly(entry("/x", Set.of(new SecretReference("b"))));
  }

  @Test
  void shouldNotStoreSecretFromScalarTargetReplacedByNestedTarget() {
    // given - a scalar target 'a' is replaced by a nested target 'a.b', so 'a' becomes a context
    // and its scalar source is dropped from the generated mapping expression
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.secrets.s1", "a")
                    .zeebeInputExpression("camunda.secrets.s2", "a.b"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then - the overridden scalar 'a' contributes no secret; only the surviving /a/b remains
    assertThat(secretReferences).containsExactly(entry("/a/b", Set.of(new SecretReference("s2"))));
  }

  @Test
  void shouldStoreOnlyEffectiveSecretWhenTargetIsOverriddenByScalarTarget() {
    // given
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.secrets.s1", "a.b")
                    .zeebeInputExpression("camunda.secrets.s2", "a"));

    // when
    final var secretReferences = task.getSecretReferences();

    // then
    assertThat(secretReferences).containsExactly(entry("/a", Set.of(new SecretReference("s2"))));
  }

  private ExecutableFlowNode transform(final Consumer<ServiceTaskBuilder> modifier) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                TASK_ID,
                t -> {
                  t.zeebeJobType("test");
                  modifier.accept(t);
                })
            .endEvent()
            .done();

    final List<ExecutableProcess> processes = transformer.transformDefinitions(model);
    return processes.getFirst().getElementById(TASK_ID, ExecutableFlowNode.class);
  }
}
