/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionRequirementsMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Deployment;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FormMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import org.junit.Test;

public final class DeployResourceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponseWithDefaultTenantId() {
    // given
    final var stub = new DeployResourceStub();
    stub.registerWith(brokerClient);

    final String bpmnName = "testProcess.bpmn";
    final String dmnName = "testDecision.dmn";
    final String formName = "testForm.form";
    final String defaultTenantId = "<default>";

    final var builder = DeployResourceRequest.newBuilder();
    builder.addResourcesBuilder().setName(bpmnName).setContent(ByteString.copyFromUtf8("<xml/>"));
    builder.addResourcesBuilder().setName(dmnName).setContent(ByteString.copyFromUtf8("test"));
    builder.addResourcesBuilder().setName(formName).setContent(ByteString.copyFromUtf8("{}"));

    final var request = builder.build();

    // when
    final var response = client.deployResource(request);

    // then
    assertThat(response.getKey()).isEqualTo(stub.getKey());
    assertThat(response.getDeploymentsCount()).isEqualTo(4);
    assertThat(response.getTenantId()).isEqualTo(defaultTenantId);

    final Deployment firstDeployment = response.getDeployments(0);
    assertThat(firstDeployment.hasProcess()).isTrue();
    assertThat(firstDeployment.hasDecision()).isFalse();
    assertThat(firstDeployment.hasDecisionRequirements()).isFalse();
    assertThat(firstDeployment.hasForm()).isFalse();
    final ProcessMetadata process = firstDeployment.getProcess();
    assertThat(process.getBpmnProcessId()).isEqualTo(bpmnName);
    assertThat(process.getResourceName()).isEqualTo(bpmnName);
    assertThat(process.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
    assertThat(process.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(process.getTenantId()).isEqualTo(defaultTenantId);

    final Deployment secondDeployment = response.getDeployments(1);
    assertThat(secondDeployment.hasProcess()).isFalse();
    assertThat(secondDeployment.hasDecision()).isTrue();
    assertThat(secondDeployment.hasDecisionRequirements()).isFalse();
    assertThat(secondDeployment.hasForm()).isFalse();
    final DecisionMetadata decision = secondDeployment.getDecision();
    assertThat(decision.getDmnDecisionId()).isEqualTo(dmnName);
    assertThat(decision.getDmnDecisionName()).isEqualTo(dmnName);
    assertThat(decision.getVersion()).isEqualTo(456);
    assertThat(decision.getDecisionKey()).isEqualTo(567);
    assertThat(decision.getDmnDecisionRequirementsId()).isEqualTo(dmnName);
    assertThat(decision.getDecisionRequirementsKey()).isEqualTo(678);
    assertThat(decision.getTenantId()).isEqualTo(defaultTenantId);

    final Deployment thirdDeployment = response.getDeployments(2);
    assertThat(thirdDeployment.hasProcess()).isFalse();
    assertThat(thirdDeployment.hasDecision()).isFalse();
    assertThat(thirdDeployment.hasDecisionRequirements()).isTrue();
    assertThat(thirdDeployment.hasForm()).isFalse();
    final DecisionRequirementsMetadata drg = thirdDeployment.getDecisionRequirements();
    assertThat(drg.getDmnDecisionRequirementsId()).isEqualTo(dmnName);
    assertThat(drg.getDmnDecisionRequirementsName()).isEqualTo(dmnName);
    assertThat(drg.getVersion()).isEqualTo(456);
    assertThat(drg.getDecisionRequirementsKey()).isEqualTo(678);
    assertThat(drg.getResourceName()).isEqualTo(dmnName);
    assertThat(drg.getTenantId()).isEqualTo(defaultTenantId);

    final Deployment fourthDeployment = response.getDeployments(3);
    assertThat(fourthDeployment.hasForm()).isTrue();
    assertThat(fourthDeployment.hasProcess()).isFalse();
    assertThat(fourthDeployment.hasDecision()).isFalse();
    assertThat(fourthDeployment.hasDecisionRequirements()).isFalse();
    final FormMetadata form = fourthDeployment.getForm();
    assertThat(form.getFormId()).isEqualTo("test-form-id");
    assertThat(form.getVersion()).isEqualTo(12);
    assertThat(form.getFormKey()).isEqualTo(11115647343L);
    assertThat(form.getResourceName()).isEqualTo(formName);
    assertThat(form.getTenantId()).isEqualTo(defaultTenantId);

    final BrokerDeployResourceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(DeploymentIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.DEPLOYMENT);
  }
}
