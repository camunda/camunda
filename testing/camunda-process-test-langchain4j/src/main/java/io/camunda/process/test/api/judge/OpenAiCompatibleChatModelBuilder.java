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
package io.camunda.process.test.api.judge;

import static io.camunda.process.test.api.judge.ModelBuilderSupport.require;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

final class OpenAiCompatibleChatModelBuilder {

  private OpenAiCompatibleChatModelBuilder() {}

  static ChatModel build(final JudgeConfigurationData data) {
    final String model = require(data.getModel(), "model", "openai-compatible");
    final String baseUrl = require(data.getBaseUrl(), "baseUrl", "openai-compatible");
    final String apiKey = data.getApiKey();

    final OpenAiChatModel.OpenAiChatModelBuilder builder =
        OpenAiChatModel.builder().baseUrl(baseUrl).modelName(model);

    if (apiKey != null) {
      builder.apiKey(apiKey);
    }

    return builder.build();
  }
}
