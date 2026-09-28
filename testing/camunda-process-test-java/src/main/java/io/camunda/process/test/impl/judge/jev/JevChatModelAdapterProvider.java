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
package io.camunda.process.test.impl.judge.jev;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterProvider;
import io.camunda.process.test.api.judge.ProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import org.apache.commons.lang3.StringUtils;

/**
 * SPI provider that creates a {@link JevChatModelAdapter} from a {@code judge.chatModel.provider =
 * jev} configuration (property file, Spring properties, or programmatic {@link
 * BaseProviderConfig.JevConfig}).
 */
public class JevChatModelAdapterProvider implements ChatModelAdapterProvider {

  @Override
  public String getProviderName() {
    return BaseProviderConfig.PROVIDER_JEV;
  }

  @Override
  public ChatModelAdapter create(final ProviderConfig config) {
    final BaseProviderConfig.JevConfig jevConfig = (BaseProviderConfig.JevConfig) config;

    if (StringUtils.isBlank(jevConfig.getApiKey())) {
      throw new IllegalStateException(
          "judge.chatModel.apiKey must be set for the 'jev' provider "
              + "(e.g. via judge.chatModel.apiKey or the CAMUNDA_PROCESSTEST_JUDGE_CHATMODEL_APIKEY env var).");
    }

    return new JevChatModelAdapter(
        jevConfig.getApiKey(), jevConfig.getBaseUrl(), jevConfig.getModel());
  }
}
