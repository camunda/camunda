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
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference;
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

class FlowNodeClusterVariableReferenceTest {

  private static final String TASK_ID = "task";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  @Test
  void shouldStoreClusterVariableReferenceKeyedByJsonPointer() {
    // given
    final var task =
        transform(
            t -> t.zeebeInputExpression("\"prefix\" + camunda.vars.env.myVar", "tokens.value"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - the dotted target path is stored as a JSON pointer
    assertThat(clusterVariableReferences)
        .containsExactly(
            entry("/tokens/value", Set.of(new ClusterVariableReference("env", "myVar"))));
  }

  @Test
  void shouldStoreClusterVariableReferenceWithFieldPath() {
    // given
    final var task =
        transform(t -> t.zeebeInputExpression("camunda.vars.tenant.myVar.field", "a.b"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences)
        .containsExactly(
            entry(
                "/a/b", Set.of(new ClusterVariableReference("tenant", "myVar", List.of("field")))));
  }

  @Test
  void shouldStoreMultipleReferencesForSingleInputMapping() {
    // given
    final var task =
        transform(
            t -> t.zeebeInputExpression("camunda.vars.env.a + camunda.vars.env.b", "tokens.value"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences)
        .containsExactly(
            entry(
                "/tokens/value",
                Set.of(
                    new ClusterVariableReference("env", "a"),
                    new ClusterVariableReference("env", "b"))));
  }

  @Test
  void shouldStoreReferencesFromMultipleInputMappings() {
    // given
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.vars.env.a", "auth.a")
                    .zeebeInputExpression("camunda.vars.env.b", "auth.b"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "/auth/a", Set.of(new ClusterVariableReference("env", "a")),
                "/auth/b", Set.of(new ClusterVariableReference("env", "b"))));
  }

  @Test
  void shouldStoreClusterVariablesForSiblingNestedTargets() {
    // given - two mappings under the same parent target, each referencing a different variable
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.vars.env.v1", "a.b")
                    .zeebeInputExpression("camunda.vars.env.v2", "a.c"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - each sibling leaf keeps its own pointer
    assertThat(clusterVariableReferences)
        .containsExactly(
            entry("/a/b", Set.of(new ClusterVariableReference("env", "v1"))),
            entry("/a/c", Set.of(new ClusterVariableReference("env", "v2"))));
  }

  @Test
  void shouldScopeJsonPointerToContextEntryOfReference() {
    // given - the source is a FEEL context; only one entry holds a cluster-variable reference
    final var task =
        transform(
            t ->
                t.zeebeInputExpression(
                    "{x1: \"camunda.vars.env.x\", x2: camunda.vars.env.x}", "foo"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - the pointer targets the entry that references the variable, not the whole target
    assertThat(clusterVariableReferences)
        .containsExactly(entry("/foo/x2", Set.of(new ClusterVariableReference("env", "x"))));
  }

  @Test
  void shouldNotStoreClusterVariableReferenceUsedAsTarget() {
    // given - the reference-looking path is the target, the source holds no reference
    final var task = transform(t -> t.zeebeInputExpression("someVariable", "camunda.vars.env.x"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences).isEmpty();
  }

  @Test
  void shouldStoreClusterVariableReferencesByJsonPointerForNestedTargetPath() {
    // given - a nested (multi-segment) target path
    final var task =
        transform(t -> t.zeebeInputExpression("camunda.vars.env.myVar", "auth.headers.token"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - each dot becomes a pointer segment
    assertThat(clusterVariableReferences)
        .containsExactly(
            entry("/auth/headers/token", Set.of(new ClusterVariableReference("env", "myVar"))));
  }

  @Test
  void shouldNotStoreClusterVariableReferenceFromOutputMapping() {
    // given - a cluster-variable reference outside of an input mapping stays a literal
    final var task = transform(t -> t.zeebeOutputExpression("camunda.vars.env.myVar", "result"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences).isEmpty();
  }

  @Test
  void shouldNotStoreClusterVariableReferenceUsedAsStringLiteral() {
    // given - the reference is a string literal, not an expression
    final var task =
        transform(t -> t.zeebeInputExpression("\"camunda.vars.env.myVar\"", "tokens.value"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences).isEmpty();
  }

  @Test
  void shouldNotStoreClusterVariableReferenceFromStaticInputMapping() {
    // given - a static (non-expression) input mapping source is a literal
    final var task = transform(t -> t.zeebeInput("camunda.vars.env.myVar", "tokens.value"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences).isEmpty();
  }

  @Test
  void shouldHaveEmptyClusterVariableReferencesWithoutInputMappings() {
    // given
    final var task = transform(t -> {});

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences).isEmpty();
  }

  @Test
  void shouldStoreOnlyEffectiveClusterVariableWhenTargetIsOverridden() {
    // given - two input mappings with the same target; the later one overrides the earlier, so
    // the generated mapping expression keeps only the last source for that target
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.vars.env.a", "x")
                    .zeebeInputExpression("camunda.vars.env.b", "x"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - only the effective (last) mapping's reference is stored, not the overridden one
    assertThat(clusterVariableReferences)
        .containsExactly(entry("/x", Set.of(new ClusterVariableReference("env", "b"))));
  }

  @Test
  void shouldNotDetectClusterVariablesAndSecretsIntoEachOthersMaps() {
    // given - one mapping references a secret, another references a cluster variable
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.secrets.token", "auth.secret")
                    .zeebeInputExpression("camunda.vars.env.region", "auth.region"));

    // when
    final var secretReferences = task.getSecretReferences();
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - each detector only reports its own kind of reference
    assertThat(secretReferences)
        .containsExactly(entry("/auth/secret", Set.of(new SecretReference("token"))));
    assertThat(secretReferences).doesNotContainKey("/auth/region");

    assertThat(clusterVariableReferences)
        .containsExactly(
            entry("/auth/region", Set.of(new ClusterVariableReference("env", "region"))));
    assertThat(clusterVariableReferences).doesNotContainKey("/auth/secret");
  }

  @Test
  void shouldNotStoreClusterVariableFromScalarTargetReplacedByNestedTarget() {
    // given - a scalar target 'a' is replaced by a nested target 'a.b', so 'a' becomes a context
    // and its scalar source is dropped from the generated mapping
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.vars.env.s1", "a")
                    .zeebeInputExpression("camunda.vars.env.s2", "a.b"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then - the overridden scalar 'a' contributes nothing; only the surviving /a/b remains
    assertThat(clusterVariableReferences)
        .containsExactly(entry("/a/b", Set.of(new ClusterVariableReference("env", "s2"))));
  }

  @Test
  void shouldStoreOnlyEffectiveClusterVariableWhenTargetIsOverriddenByScalarTarget() {
    // given
    final var task =
        transform(
            t ->
                t.zeebeInputExpression("camunda.vars.env.s1", "a.b")
                    .zeebeInputExpression("camunda.vars.env.s2", "a"));

    // when
    final var clusterVariableReferences = task.getClusterVariableReferences();

    // then
    assertThat(clusterVariableReferences)
        .containsExactly(entry("/a", Set.of(new ClusterVariableReference("env", "s2"))));
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
