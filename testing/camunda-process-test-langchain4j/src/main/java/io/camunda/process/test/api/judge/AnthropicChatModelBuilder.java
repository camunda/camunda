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

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;

final class AnthropicChatModelBuilder {

  private AnthropicChatModelBuilder() {}

  static ChatModel build(final JudgeConfigBootstrapData data) {
    final String model = require(data.getModel(), "model", "anthropic");
    final String apiKey = require(data.getApiKey(), "apiKey", "anthropic");

    return AnthropicChatModel.builder().apiKey(apiKey).modelName(model).build();
  }
}
