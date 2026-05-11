/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.variable.EventApplyingStateWriter;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies that {@link DeploymentTransformer#transform} surfaces the categories of every
 * transformer that was dispatched during a failed deployment. The set is used downstream to decide
 * which state caches to invalidate after a rollback. Misclassification here previously caused
 * caches not to be cleared for generic (non-RPA) resources — see issue #50908.
 */
@ExtendWith(ProcessingStateExtension.class)
final class DeploymentTransformerTouchedCategoriesTest {

  private static final byte[] INVALID_BPMN_BYTES =
      Bpmn.convertToString(Bpmn.createExecutableProcess("invalid_no_start_event").done())
          .getBytes(StandardCharsets.UTF_8);

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private DeploymentTransformer deploymentTransformer;

  @BeforeEach
  void beforeEach() {
    final var eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
    final var stateWriter =
        new EventApplyingStateWriter(new RecordingTypedEventWriter(), eventAppliers);
    final var expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.system()));
    final var expressionProcessor =
        new ExpressionProcessor(
            expressionLanguage,
            variableName -> Either.left(null),
            EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT);

    deploymentTransformer =
        new DeploymentTransformer(
            stateWriter,
            processingState,
            expressionProcessor,
            processingState.getKeyGenerator(),
            FeatureFlags.createDefault(),
            ValidationConfig.builder()
                .withMaxIdFieldLength(100)
                .withMaxNameFieldLength(100)
                .withMaxWorkerTypeLength(100)
                .withValidatorResultsOutputMaxSize(1000)
                .build(),
            InstantSource.system(),
            ExpressionLanguageMetrics.noop());
  }

  @Test
  void shouldTrackBpmnCategoryWhenBpmnDeploymentFails() {
    // given
    final var deployment = deploymentWith("invalid.bpmn", INVALID_BPMN_BYTES);

    // when
    final var result = deploymentTransformer.transform(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().touchedCategories())
        .containsExactly(DeploymentResourceCategory.BPMN);
  }

  @Test
  void shouldTrackResourceCategoryWhenGenericXmlIsDeployedAlongsideFailingBpmn() {
    // given — a non-BPMN .xml file is a generic resource. Before the fix, the extension-based
    // predicate on DeploymentRecord misclassified it as BPMN and skipped resourceState
    // invalidation.
    final var deployment =
        deploymentWith("config.xml", "<config>not bpmn</config>".getBytes(StandardCharsets.UTF_8));
    addResource(deployment, "invalid.bpmn", INVALID_BPMN_BYTES);

    // when
    final var result = deploymentTransformer.transform(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().touchedCategories())
        .containsExactlyInAnyOrder(
            DeploymentResourceCategory.BPMN, DeploymentResourceCategory.RESOURCE);
  }

  @Test
  void shouldTrackResourceCategoryForGenericResourceWithArbitraryExtension() {
    // given — a generic resource without a known extension. Before the fix, hasResources()
    // only matched ".rpa", so this category was never tracked.
    final var deployment = deploymentWith("script.txt", "echo hi".getBytes(StandardCharsets.UTF_8));
    addResource(deployment, "invalid.bpmn", INVALID_BPMN_BYTES);

    // when
    final var result = deploymentTransformer.transform(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().touchedCategories())
        .containsExactlyInAnyOrder(
            DeploymentResourceCategory.BPMN, DeploymentResourceCategory.RESOURCE);
  }

  @Test
  void shouldTrackAllCategoriesWhenAllResourceTypesAreDeployed() {
    // given — one resource of every category. The BPMN is invalid so the deployment fails
    // after every transformer has been dispatched.
    final var deployment = deploymentWith("invalid.bpmn", INVALID_BPMN_BYTES);
    addResource(deployment, "decision.dmn", "<not-a-dmn/>".getBytes(StandardCharsets.UTF_8));
    addResource(deployment, "form.form", "{}".getBytes(StandardCharsets.UTF_8));
    addResource(deployment, "script.rpa", "{}".getBytes(StandardCharsets.UTF_8));
    addResource(deployment, "notes.txt", "hello".getBytes(StandardCharsets.UTF_8));

    // when
    final var result = deploymentTransformer.transform(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().touchedCategories())
        .containsExactlyInAnyOrder(
            DeploymentResourceCategory.BPMN,
            DeploymentResourceCategory.DMN,
            DeploymentResourceCategory.FORM,
            DeploymentResourceCategory.RESOURCE);
  }

  @Test
  void shouldReportEmptyCategoriesWhenValidationFailsBeforeDispatch() {
    // given — an empty deployment fails at validateResources(), before any transformer is
    // dispatched. No state cache could have been touched, so the set must be empty.
    final var deployment = new DeploymentRecord();

    // when
    final var result = deploymentTransformer.transform(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().touchedCategories()).isEmpty();
  }

  private static DeploymentRecord deploymentWith(final String resourceName, final byte[] content) {
    final var record = new DeploymentRecord();
    addResource(record, resourceName, content);
    return record;
  }

  private static void addResource(
      final DeploymentRecord record, final String resourceName, final byte[] content) {
    record
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapArray(content));
  }
}
