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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.ExtensionElements;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockDmnDecisionTest {

  private static final String ZEEBE_NAMESPACE = "http://camunda.org/schema/zeebe/1.0";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Captor private ArgumentCaptor<InputStream> dmnStreamCaptor;

  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    processTestContext =
        new CamundaProcessTestContextImpl(
            camundaProcessTestRuntime,
            clientCreationCallback,
            clockClient,
            DevAwaitBehavior::expectSuccess,
            jsonMapper,
            new ConditionalBehaviorEngine(),
            () -> new CamundaDataSource(camundaClient));
  }

  @Test
  void shouldMockDmnDecisionWithoutVersionTag() {
    // given
    final String decisionId = "mock-dmn-decision-without-version-tag";
    when(camundaClient.getConfiguration().getJsonMapper().toJson("approved"))
        .thenReturn("\"approved\"");

    // when
    processTestContext.mockDmnDecision().withDecisionId(decisionId).thenReturn("approved");

    // then
    verify(camundaClient.newDeployResourceCommand())
        .addResourceStream(dmnStreamCaptor.capture(), eq(decisionId + ".dmn"));

    final DmnModelInstance deployedModel = Dmn.readModelFromStream(dmnStreamCaptor.getValue());

    assertThat(deployedModel.getModelElementsByType(Decision.class))
        .hasSize(1)
        .first()
        .satisfies(
            decision -> {
              assertThat(decision.getId()).isEqualTo(decisionId);
              assertThat(decision.getChildElementsByType(LiteralExpression.class))
                  .hasSize(1)
                  .first()
                  .satisfies(
                      expression ->
                          assertThat(expression.getText().getTextContent())
                              .isEqualTo("\"approved\""));
              assertThat(zeebeVersionTag(decision)).isEmpty();
            });
  }

  private static Optional<String> zeebeVersionTag(final Decision decision) {
    return Optional.ofNullable(decision.getUniqueChildElementByType(ExtensionElements.class))
        .map(
            extensionElements ->
                extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, "versionTag"))
        .map(versionTag -> versionTag.getAttributeValue("value"));
  }
}
