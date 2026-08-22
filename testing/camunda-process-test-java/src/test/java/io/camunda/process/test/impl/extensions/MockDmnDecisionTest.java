/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.io.InputStream;
import java.util.function.Consumer;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.camunda.bpm.model.dmn.instance.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockDmnDecisionTest {

  private static final String DECISION_ID = "decision-1";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;
  @Mock private Consumer<DeploymentEvent> deploymentCallback;
  @Mock private DeploymentEvent deploymentEvent;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Captor private ArgumentCaptor<InputStream> modelStreamCaptor;

  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);
    when(camundaClient.newDeployResourceCommand().addResourceStream(any(), any()).send().join())
        .thenReturn(deploymentEvent);

    processTestContext =
        new CamundaProcessTestContextImpl(
            camundaProcessTestRuntime,
            clientCreationCallback,
            deploymentCallback,
            clockClient,
            DevAwaitBehavior::expectSuccess,
            jsonMapper,
            new ConditionalBehaviorEngine(),
            () -> new CamundaDataSource(camundaClient));
  }

  @Test
  void shouldMockDmnDecision() {
    // given
    when(camundaClient.getConfiguration().getJsonMapper().toJson("approved"))
        .thenReturn("\"approved\"");

    // when
    processTestContext.mockDmnDecision(DECISION_ID, "approved");

    // then
    verify(camundaClient.newDeployResourceCommand())
        .addResourceStream(modelStreamCaptor.capture(), eq(DECISION_ID + ".dmn"));

    final DmnModelInstance modelInstance = Dmn.readModelFromStream(modelStreamCaptor.getValue());
    final Decision decision = modelInstance.getModelElementById(DECISION_ID);
    assertThat(decision).isNotNull();
    final LiteralExpression literalExpression =
        decision.getChildElementsByType(LiteralExpression.class).iterator().next();
    final Text text = literalExpression.getText();
    assertThat(text.getTextContent()).isEqualTo("\"approved\"");
  }

  @Test
  void shouldInvokeDeploymentCallbackWhenMockingDmnDecision() {
    // when
    processTestContext.mockDmnDecision(DECISION_ID, "approved");

    // then
    verify(deploymentCallback).accept(deploymentEvent);
  }
}
