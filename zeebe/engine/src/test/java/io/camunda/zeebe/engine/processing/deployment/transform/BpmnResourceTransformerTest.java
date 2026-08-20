/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.metrics.ProcessDefinitionMetrics;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class BpmnResourceTransformerTest {

  private BpmnResourceTransformer transformer;

  @BeforeEach
  void setUp() {
    transformer =
        new BpmnResourceTransformer(
            mock(KeyGenerator.class),
            mock(StateWriter.class),
            new ChecksumGenerator(),
            mock(ProcessState.class),
            mock(ExpressionProcessor.class),
            false,
            ValidationConfig.builder().build(),
            InstantSource.system(),
            ExpressionLanguageMetrics.noop(),
            mock(ProcessDefinitionMetrics.class));
  }

  @Test
  void shouldPutParsedModelWhenCanTransformAcceptsXmlBpmn() {
    // given
    final DeploymentResource resource = bpmnResource("process.xml");

    // when
    final boolean accepted = transformer.canTransform(resource);

    // then
    assertThat(accepted).isTrue();
    assertThat(transformer.hasParsedModelFor(resource)).isTrue();
  }

  @Test
  void shouldNotPutParsedModelForBpmnExtension() {
    // given
    final DeploymentResource resource = bpmnResource("process.bpmn");

    // when
    final boolean accepted = transformer.canTransform(resource);

    // then
    assertThat(accepted).isTrue();
    assertThat(transformer.hasParsedModelFor(resource)).isFalse();
  }

  @Test
  void shouldNotPutParsedModelWhenXmlIsNotValidBpmn() {
    // given
    final DeploymentResource resource =
        new DeploymentResource()
            .setResourceName(wrapString("not-bpmn.xml"))
            .setResource(wrapString("<root><child/></root>"));

    // when
    final boolean accepted = transformer.canTransform(resource);

    // then
    assertThat(accepted).isFalse();
    assertThat(transformer.hasParsedModelFor(resource)).isFalse();
  }

  @Test
  void shouldConsumeParsedModelDuringCreateMetadata() {
    // given
    final DeploymentResource resource = bpmnResource("process.xml");
    assertThat(transformer.canTransform(resource)).isTrue();
    assertThat(transformer.hasParsedModelFor(resource)).isTrue();

    // when
    final var result = transformer.createMetadata(resource, new DeploymentRecord());

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(transformer.hasParsedModelFor(resource))
        .as(
            "parsed models should be consumed by createMetadata to avoid leaking across deployments")
        .isFalse();
  }

  @Test
  void shouldClearParsedModelsOnReset() {
    // given
    final DeploymentResource resource = bpmnResource("process.xml");
    assertThat(transformer.canTransform(resource)).isTrue();
    assertThat(transformer.hasParsedModelFor(resource)).isTrue();

    // when
    transformer.reset();

    // then
    assertThat(transformer.hasParsedModelFor(resource)).isFalse();
  }

  private static DeploymentResource bpmnResource(final String resourceName) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    return new DeploymentResource()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapString(Bpmn.convertToString(model)));
  }
}
