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
package io.camunda.process.test.impl.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.process.test.impl.BedrockRuntimeClientFactory;
import io.camunda.process.test.impl.judge.BaseProviderConfig.AmazonBedrockConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockChatModelBuilderTest {

  private static final String MODEL = "anthropic.claude-3-5-sonnet-20241022-v2:0";
  private static final String REGION = "us-east-1";
  private static final String ACCESS_KEY = "test-access-key";
  private static final String SECRET_KEY = "test-secret-key";
  private static final String API_KEY = "test-api-key";

  // -- Happy paths --

  @Test
  void shouldBuildChatModel() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(MODEL, REGION, null, ACCESS_KEY, SECRET_KEY);

    // when
    final ChatModel chatModel = BedrockChatModelBuilder.build(config);

    // then
    assertThat(chatModel).isNotNull();
  }

  @Test
  void shouldSetRequiredFieldsOnBuilder() {
    // given
    final AmazonBedrockConfig config = new AmazonBedrockConfig(MODEL, REGION, null, null, null);
    final BedrockChatModel.Builder mockBuilder = mock(BedrockChatModel.Builder.class);

    // when
    BedrockChatModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).modelId(MODEL);
    verify(mockBuilder).client(any());
  }

  @Test
  void shouldApplyTemperatureToBuilder() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(MODEL, REGION, null, ACCESS_KEY, SECRET_KEY);
    config.setTemperature(0.7);
    final BedrockChatModel.Builder mockBuilder = mock(BedrockChatModel.Builder.class);
    final ArgumentCaptor<BedrockChatRequestParameters> captor =
        ArgumentCaptor.forClass(BedrockChatRequestParameters.class);

    // when
    BedrockChatModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder, never()).timeout(any());
    verify(mockBuilder).defaultRequestParameters(captor.capture());
    assertThat(captor.getValue().temperature()).isEqualTo(0.7);
  }

  @Test
  void shouldForwardConfigToClientFactory() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(MODEL, REGION, API_KEY, ACCESS_KEY, SECRET_KEY);
    config.setTimeout(Duration.ofSeconds(60));
    final BedrockChatModel.Builder mockBuilder = mock(BedrockChatModel.Builder.class);

    try (final MockedStatic<BedrockRuntimeClientFactory> mockFactory =
        mockStatic(BedrockRuntimeClientFactory.class)) {
      mockFactory
          .when(() -> BedrockRuntimeClientFactory.build(any(), any(), any(), any(), any()))
          .thenReturn(mock(BedrockRuntimeClient.class));

      // when
      BedrockChatModelBuilder.build(config, mockBuilder);

      // then
      mockFactory.verify(
          () ->
              BedrockRuntimeClientFactory.build(
                  eq(REGION),
                  eq(API_KEY),
                  eq(ACCESS_KEY),
                  eq(SECRET_KEY),
                  eq(Duration.ofSeconds(60))));
    }
  }

  // -- Required field validation --

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(model, REGION, null, ACCESS_KEY, SECRET_KEY);

    // when / then
    assertThatThrownBy(() -> BedrockChatModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining("amazon-bedrock");
  }
}
