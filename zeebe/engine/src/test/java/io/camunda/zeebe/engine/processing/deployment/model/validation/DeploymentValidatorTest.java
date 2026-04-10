/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentResourceContext;
import io.camunda.zeebe.engine.processing.deployment.transform.ValidationConfig;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentValidatorTest {

  private static final int MAX_ID_LENGTH = 256;
  private static final int MAX_NAME_LENGTH = 256;

  private final DeploymentValidator validator =
      new DeploymentValidator(new ValidationConfig(MAX_ID_LENGTH, MAX_NAME_LENGTH));

  // --- validateResources ---

  @Test
  void shouldRejectEmptyDeployment() {
    // given
    final var deployment = new DeploymentRecord();

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .isEqualTo("Expected to deploy at least one resource, but none given");
  }

  @Test
  void shouldAcceptDeploymentWithResources() {
    // given
    final var deployment = new DeploymentRecord();
    deployment.resources().add().setResourceName("process.bpmn");

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectResourceNameExceedingMaxLength() {
    // given
    final var deployment = new DeploymentRecord();
    final var longName = "a".repeat(MAX_NAME_LENGTH + 1) + ".bpmn";
    deployment.resources().add().setResourceName(longName);

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("exceeds maximum length of %d characters".formatted(MAX_NAME_LENGTH));
  }

  @Test
  void shouldAcceptResourceNameAtMaxLength() {
    // given
    final var deployment = new DeploymentRecord();
    final var name = "a".repeat(MAX_NAME_LENGTH);
    deployment.resources().add().setResourceName(name);

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldCollectMultipleResourceNameErrors() {
    // given
    final var deployment = new DeploymentRecord();
    final var longName1 = "a".repeat(MAX_NAME_LENGTH + 1) + ".bpmn";
    final var longName2 = "b".repeat(MAX_NAME_LENGTH + 1) + ".dmn";
    deployment.resources().add().setResourceName(longName1);
    deployment.resources().add().setResourceName(longName2);

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    final var message = result.getLeft().getMessage();
    assertThat(message).contains(longName1).contains(longName2);
  }

  // --- validateMetadata: duplicate process IDs ---

  @Test
  void shouldRejectDuplicateProcessIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process1")
        .setResourceName("file1.bpmn");
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process1")
        .setResourceName("file2.bpmn");

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("Duplicated process id")
        .contains("file1.bpmn")
        .contains("file2.bpmn");
  }

  @Test
  void shouldAcceptUniqueProcessIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process1")
        .setResourceName("file1.bpmn");
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process2")
        .setResourceName("file2.bpmn");

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  // --- validateMetadata: duplicate form IDs ---

  @Test
  void shouldRejectDuplicateFormIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment.formMetadata().add().setFormId("form1").setResourceName("form1.form");
    deployment.formMetadata().add().setFormId("form1").setResourceName("form1-copy.form");

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("duplicated id 'form1'")
        .contains("form1.form")
        .contains("form1-copy.form");
  }

  // --- validateMetadata: duplicate resource IDs ---

  @Test
  void shouldRejectDuplicateResourceIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("my-script.txt")
        .setResourceName("my-script.txt");
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("my-script.txt")
        .setResourceName("my-script.txt");

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("duplicated id 'my-script.txt'")
        .contains("my-script.txt");
  }

  @Test
  void shouldAcceptUniqueResourceIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("script1.txt")
        .setResourceName("script1.txt");
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("script2.txt")
        .setResourceName("script2.txt");

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  // --- validateMetadata: duplicate DRG IDs ---

  @Test
  void shouldRejectDuplicateDecisionRequirementsIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg1")
        .setResourceName("decisions1.dmn");
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg1")
        .setResourceName("decisions2.dmn");

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("duplicated id 'drg1'")
        .contains("decisions1.dmn")
        .contains("decisions2.dmn");
  }

  // --- error message formatting ---

  @Test
  void shouldPrefixErrorMessageCorrectly() {
    // given
    final var deployment = new DeploymentRecord();

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    // Empty deployment error doesn't use the prefix (it's a standalone message)
    assertThat(result.getLeft().getMessage()).startsWith("Expected to deploy at least one resource");
  }

  @Test
  void shouldIncludeErrorPrefixForResourceNameErrors() {
    // given
    final var deployment = new DeploymentRecord();
    final var longName = "a".repeat(MAX_NAME_LENGTH + 1) + ".bpmn";
    deployment.resources().add().setResourceName(longName);

    // when
    final var result = validator.validateResources(deployment);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .startsWith("Expected to deploy new resources, but encountered the following errors:");
  }

  // --- validateMetadata with empty contexts ---

  @Test
  void shouldAcceptEmptyDeploymentMetadata() {
    // given - no metadata at all (no processes, no decisions, no forms, no resources)
    final var deployment = new DeploymentRecord();

    // when
    final var result = validator.validateMetadata(deployment, List.of());

    // then
    assertThat(result.isRight()).isTrue();
  }
}
