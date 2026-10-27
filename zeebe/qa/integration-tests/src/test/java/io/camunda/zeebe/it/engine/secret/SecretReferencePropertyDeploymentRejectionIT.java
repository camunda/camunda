/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage that a {@code camunda.secrets.<name>} reference written as a plaintext string
 * literal in a {@code zeebe:property} value is rejected at deploy time, against a real gateway and
 * a real client — the property counterpart to the {@code zeebe:input} guard.
 *
 * <p>The engine suites ({@code SecretReferenceLiteralValidatorTest}, {@code
 * SecretReferenceInputMappingTest}) already cover the validation rule and its input-mapping form
 * with an in-process engine. What only this level shows is that a rejected deployment surfaces to
 * the client as a {@link ClientStatusException} carrying the reason, so the whole gateway to engine
 * rejection path holds for properties. A literal reference is silently never resolved at runtime,
 * so catching it at deployment is what stops the raw placeholder from leaking.
 *
 * <p>The guard is a static deploy-time check that never resolves the reference, so the broker runs
 * with no secret store configured.
 */
@ZeebeIntegration
final class SecretReferencePropertyDeploymentRejectionIT {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();
  private final String propertyName = "authToken";
  // a FEEL reference is a path, so the name is kept to a bare identifier that needs no escaping
  private final String secretName = "s" + UUID.randomUUID().toString().replace("-", "");
  private final String secretReference = "camunda.secrets." + secretName;

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker = new TestStandaloneBroker().withUnauthenticatedAccess();
  }

  @Test
  void shouldRejectStaticPlaintextSecretReferenceInPropertyValue() {
    // given - a static property value (no leading '=') equal to a secret reference is a literal
    final var process = processWithProperty(secretReference);

    // when / then - the deployment is rejected and the reason reaches the client
    assertThatThrownBy(() -> deploy(process))
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(secretReference)
        .hasMessageContaining("must be used as an expression");
  }

  @Test
  void shouldRejectFeelStringLiteralSecretReferenceInPropertyValue() {
    // given - a FEEL string literal property value equal to a secret reference
    final var process = processWithProperty("=\"" + secretReference + "\"");

    // when / then
    assertThatThrownBy(() -> deploy(process))
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(secretReference)
        .hasMessageContaining("must be used as an expression");
  }

  @Test
  void shouldAcceptSecretReferenceExpressionInPropertyValue() {
    // given - the reference is a bare FEEL expression path, the only allowed form
    final var process = processWithProperty("=" + secretReference);

    // when / then - the guard does not over-reject a valid expression
    assertThatCode(() -> deploy(process)).doesNotThrowAnyException();
  }

  private BpmnModelInstance processWithProperty(final String propertyValue) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(
            "task", task -> task.zeebeJobType(jobType).zeebeProperty(propertyName, propertyValue))
        .endEvent()
        .done();
  }

  private void deploy(final BpmnModelInstance process) {
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();
  }
}
