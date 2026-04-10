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

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.impl.configuration.JudgeConfiguration;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationContext;

/**
 * Resolves a {@link JudgeConfig} by first checking for Spring beans, then falling back to SPI
 * providers.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>If exactly one {@link ChatModelAdapter} bean exists and no provider is configured, use it.
 *   <li>If a provider is configured and a bean named {@code "<provider>"} exists, use that bean.
 *   <li>Otherwise, fall back to SPI-based resolution via {@link ChatModelAdapterResolver}.
 * </ol>
 */
public final class JudgeConfigResolver {

  private JudgeConfigResolver() {}

  public static Optional<JudgeConfig> resolve(
      final ApplicationContext applicationContext, final JudgeConfiguration judgeConfiguration) {
    return resolveAdapter(applicationContext, judgeConfiguration)
        .map(
            adapter ->
                JudgeConfig.of(
                    adapter,
                    judgeConfiguration.getThreshold(),
                    judgeConfiguration.getCustomPrompt()));
  }

  private static Optional<ChatModelAdapter> resolveAdapter(
      final ApplicationContext applicationContext, final JudgeConfiguration judgeConfiguration) {
    final ChatModelAdapter beanAdapter = resolveBeanAdapter(applicationContext, judgeConfiguration);
    if (beanAdapter != null) {
      return Optional.of(beanAdapter);
    }

    if (!judgeConfiguration.hasProviderConfigured()) {
      return Optional.empty();
    }

    final Optional<ChatModelAdapter> spiAdapter =
        ChatModelAdapterResolver.resolve(judgeConfiguration.toProviderConfig());
    if (spiAdapter.isEmpty()) {
      throw new IllegalStateException(
          "Judge configuration is present but no ChatModelAdapterProvider could be resolved. "
              + "Ensure judge.chatModel.provider is configured and "
              + "the appropriate provider module is on the classpath.");
    }
    return spiAdapter;
  }

  private static ChatModelAdapter resolveBeanAdapter(
      final ApplicationContext applicationContext, final JudgeConfiguration judgeConfiguration) {
    final Map<String, ChatModelAdapter> beans =
        applicationContext.getBeansOfType(ChatModelAdapter.class);

    if (beans.isEmpty()) {
      return null;
    }

    // Single bean without provider configured: auto-select
    if (beans.size() == 1 && !judgeConfiguration.hasProviderConfigured()) {
      return beans.values().iterator().next();
    }

    // Provider configured: match by provider bean name
    if (judgeConfiguration.hasProviderConfigured()) {
      final String provider = judgeConfiguration.getChatModel().getProvider().trim();
      return beans.get(provider);
    }

    return null;
  }
}
