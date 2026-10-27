/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import org.junit.Rule;
import org.junit.Test;

public class LinkedResourceDeploymentBindingTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldRejectDeploymentIfLinkedResourceNotIncluded() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "serviceTask",
                builder ->
                    builder
                        .zeebeLinkedResources(
                            l ->
                                l.resourceId("test-rpa-resource")
                                    .resourceType("RPA")
                                    .bindingType(ZeebeBindingType.deployment))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        engine.deployment().withXmlResource("process.bpmn", process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .isEqualTo(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.bpmn':
            - Element: serviceTask > extensionElements > linkedResources > linkedResource
                - ERROR: Expected to find resource with id 'test-rpa-resource' in current deployment, but not found.
            """);
  }

  @Test
  public void shouldDeploySuccessfullyIfLinkedResourceIncluded() {
    // given
    final var rpaResource =
        """
        {
          "id": "test-rpa-resource",
          "resourceType": "RPA"
        }
        """;
    final var process =
        Bpmn.createExecutableProcess("process-linked-resource-success")
            .startEvent()
            .serviceTask(
                "serviceTask",
                builder ->
                    builder
                        .zeebeLinkedResources(
                            l ->
                                l.resourceId("test-rpa-resource")
                                    .resourceType("RPA")
                                    .bindingType(ZeebeBindingType.deployment))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withJsonResource(rpaResource.getBytes(UTF_8), "resource.rpa")
            .deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldRejectDeploymentIfLinkedFormResourceNotIncluded() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process-linked-form-missing")
            .startEvent()
            .serviceTask(
                "serviceTask",
                builder ->
                    builder
                        .zeebeLinkedResources(
                            l ->
                                l.resourceId("my-form")
                                    .resourceType("form")
                                    .bindingType(ZeebeBindingType.deployment))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        engine.deployment().withXmlResource("process.bpmn", process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Expected to find form with id 'my-form' in current deployment, but not found.");
  }

  @Test
  public void shouldDeploySuccessfullyIfLinkedFormResourceIncluded() {
    // given
    final var form =
        """
        {
          "id": "my-form",
          "components": []
        }
        """;
    final var process =
        Bpmn.createExecutableProcess("process-linked-form-success")
            .startEvent()
            .serviceTask(
                "serviceTask",
                builder ->
                    builder
                        .zeebeLinkedResources(
                            l ->
                                l.resourceId("my-form")
                                    .resourceType("form")
                                    .bindingType(ZeebeBindingType.deployment))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource("process.bpmn", process)
            .withJsonResource(form.getBytes(UTF_8), "my-form.form")
            .deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldRejectIfLinkedResourceIdNotFoundInDeployment() {
    // given - a BPMN process referencing a resource ID that is not in the deployment
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "my_linked_resource",
                t ->
                    t.zeebeLinkedResources(
                            l ->
                                l.resourceId("2")
                                    .resourceType("RPA")
                                    .bindingType(ZeebeBindingType.deployment)
                                    .versionTag("1v")
                                    .linkName("my_link"))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    // when
    final var rejection = engine.deployment().withXmlResource(process).expectRejection().deploy();

    // then - rejected at deploy time (not a runtime incident)
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains("Expected to find resource with id '2' in current deployment, but not found.");
  }
}
