/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.deployment.transform.BpmnElementsWithDeploymentBinding;
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentResourceContext;
import io.camunda.zeebe.engine.processing.deployment.transform.ValidationConfig;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

class DeploymentValidatorTest {

  private static final int MAX_ID_LENGTH = 256;
  private static final int MAX_NAME_LENGTH = 256;
  private static final DirectBuffer DUMMY_CHECKSUM = BufferUtil.wrapString("checksum");

  private final DeploymentValidator validator =
      new DeploymentValidator(
          ValidationConfig.builder()
              .withMaxIdFieldLength(MAX_ID_LENGTH)
              .withMaxNameFieldLength(MAX_NAME_LENGTH)
              .build());

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
    assertThat(result.getLeft().getMessage()).contains(longName1).contains(longName2);
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

  @Test
  void shouldRejectDuplicateProcessIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process1")
        .setResourceName("file1.bpmn")
        .setChecksum(DUMMY_CHECKSUM)
        .setVersion(1)
        .setKey(1);
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process1")
        .setResourceName("file2.bpmn")
        .setChecksum(DUMMY_CHECKSUM)
        .setVersion(1)
        .setKey(2);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("Duplicated process id 'process1'")
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
        .setResourceName("file1.bpmn")
        .setChecksum(DUMMY_CHECKSUM)
        .setVersion(1)
        .setKey(1);
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("process2")
        .setResourceName("file2.bpmn")
        .setChecksum(DUMMY_CHECKSUM)
        .setVersion(1)
        .setKey(2);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectDuplicateDecisionRequirementsIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg1")
        .setResourceName("decisions1.dmn")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg1")
        .setResourceName("decisions2.dmn")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(2)
        .setChecksum(DUMMY_CHECKSUM);

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

  @Test
  void shouldAcceptUniqueDecisionRequirementsIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg1")
        .setResourceName("decisions1.dmn")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg2")
        .setResourceName("decisions2.dmn")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(2)
        .setChecksum(DUMMY_CHECKSUM);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectDuplicateDecisionIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsId("drg1")
        .setResourceName("decisions.dmn")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .decisionsMetadata()
        .add()
        .setDecisionId("decision1")
        .setDecisionRequirementsKey(1)
        .setVersion(1)
        .setDecisionKey(10);
    deployment
        .decisionsMetadata()
        .add()
        .setDecisionId("decision1")
        .setDecisionRequirementsKey(1)
        .setVersion(1)
        .setDecisionKey(11);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("duplicated id 'decision1'")
        .contains("decisions.dmn");
  }

  @Test
  void shouldRejectDuplicateFormIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .formMetadata()
        .add()
        .setFormId("form1")
        .setResourceName("form1.form")
        .setVersion(1)
        .setFormKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .formMetadata()
        .add()
        .setFormId("form1")
        .setResourceName("form1-copy.form")
        .setVersion(1)
        .setFormKey(2)
        .setChecksum(DUMMY_CHECKSUM);

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

  @Test
  void shouldAcceptUniqueFormIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .formMetadata()
        .add()
        .setFormId("form1")
        .setResourceName("form1.form")
        .setVersion(1)
        .setFormKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .formMetadata()
        .add()
        .setFormId("form2")
        .setResourceName("form2.form")
        .setVersion(1)
        .setFormKey(2)
        .setChecksum(DUMMY_CHECKSUM);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectDuplicateResourceIds() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("my-script.txt")
        .setResourceName("my-script.txt")
        .setVersion(1)
        .setResourceKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("my-script.txt")
        .setResourceName("my-script.txt")
        .setVersion(1)
        .setResourceKey(2)
        .setChecksum(DUMMY_CHECKSUM);

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
        .setResourceName("script1.txt")
        .setVersion(1)
        .setResourceKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("script2.txt")
        .setResourceName("script2.txt")
        .setVersion(1)
        .setResourceKey(2)
        .setChecksum(DUMMY_CHECKSUM);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldAcceptEmptyDeploymentMetadata() {
    // given
    final var deployment = new DeploymentRecord();

    // when
    final var result = validator.validateMetadata(deployment, List.of());

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldAcceptDeploymentBindingWhenNoBpmnContexts() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("script.txt")
        .setResourceName("script.txt")
        .setVersion(1)
        .setResourceKey(1)
        .setChecksum(DUMMY_CHECKSUM);

    // when
    final var result =
        validator.validateMetadata(deployment, List.of(DeploymentResourceContext.NONE));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectMissingCalledProcessWithDeploymentBinding() {
    // given
    final var deployment = new DeploymentRecord();
    final var elements = bpmnContextWithCallActivity("child-process");

    // when
    final var result = validator.validateMetadata(deployment, List.of(elements));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("Expected to find process with id 'child-process'");
  }

  @Test
  void shouldAcceptCalledProcessPresentInDeployment() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .processesMetadata()
        .add()
        .setBpmnProcessId("child-process")
        .setResourceName("child.bpmn")
        .setChecksum(DUMMY_CHECKSUM)
        .setVersion(1)
        .setKey(1);
    final var elements = bpmnContextWithCallActivity("child-process");

    // when
    final var result = validator.validateMetadata(deployment, List.of(elements));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldRejectMissingLinkedResourceWithDeploymentBinding() {
    // given
    final var deployment = new DeploymentRecord();
    final var elements = bpmnContextWithLinkedResource("my-script.txt");

    // when
    final var result = validator.validateMetadata(deployment, List.of(elements));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage())
        .contains("Expected to find resource with id 'my-script.txt'");
  }

  @Test
  void shouldAcceptLinkedResourcePresentInDeployment() {
    // given
    final var deployment = new DeploymentRecord();
    deployment
        .resourceMetadata()
        .add()
        .setResourceId("my-script.txt")
        .setResourceName("my-script.txt")
        .setVersion(1)
        .setResourceKey(1)
        .setChecksum(DUMMY_CHECKSUM);
    final var elements = bpmnContextWithLinkedResource("my-script.txt");

    // when
    final var result = validator.validateMetadata(deployment, List.of(elements));

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldIncludeResourceNameInBindingError() {
    // given
    final var deployment = new DeploymentRecord();
    final var elements = bpmnContextWithCallActivity("missing-process");

    // when
    final var result = validator.validateMetadata(deployment, List.of(elements));

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage()).contains("'test-process.bpmn'");
  }

  private BpmnElementsWithDeploymentBinding bpmnContextWithCallActivity(
      final String calledProcessId) {
    final var model =
        Bpmn.createExecutableProcess("parent")
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessId(calledProcessId).zeebeBindingType(ZeebeBindingType.deployment))
            .endEvent()
            .done();
    final var elements = new BpmnElementsWithDeploymentBinding("test-process.bpmn");
    model.getDefinitions().getChildElementsByType(Process.class).stream()
        .filter(Process::isExecutable)
        .forEach(elements::addFromProcess);
    return elements;
  }

  private BpmnElementsWithDeploymentBinding bpmnContextWithLinkedResource(final String resourceId) {
    final var model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeLinkedResources(
                            l ->
                                l.resourceId(resourceId)
                                    .resourceType("Script")
                                    .bindingType(ZeebeBindingType.deployment))
                        .zeebeJobType("demo"))
            .endEvent()
            .done();
    final var elements = new BpmnElementsWithDeploymentBinding("test-process.bpmn");
    model.getDefinitions().getChildElementsByType(Process.class).stream()
        .filter(Process::isExecutable)
        .forEach(elements::addFromProcess);
    return elements;
  }
}
