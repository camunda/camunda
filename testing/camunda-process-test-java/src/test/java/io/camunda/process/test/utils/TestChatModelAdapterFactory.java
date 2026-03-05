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
package io.camunda.process.test.utils;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterFactory;
import java.util.Properties;

/**
 * A test-scoped {@link ChatModelAdapterFactory} registered via SPI for verifying ServiceLoader
 * discovery in {@code JudgeConfigBootstrapTest}. Only handles {@code
 * judge.chatModel.provider=test}.
 */
public class TestChatModelAdapterFactory implements ChatModelAdapterFactory {

  @Override
  public ChatModelAdapter create(final Properties properties) {
    if ("test".equals(properties.getProperty("judge.chatModel.provider"))) {
      return prompt -> "{\"score\": 1.0, \"reasoning\": \"test factory\"}";
    }
    return null;
  }
}
