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
package io.camunda.process.test.impl.runtime;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterFactory;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.impl.runtime.properties.JudgeProperties;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

public final class JudgeConfigBootstrap {

  private JudgeConfigBootstrap() {}

  public static JudgeConfig bootstrap(
      final JudgeProperties judgeProperties, final Properties rawProperties) {

    if (!judgeProperties.isChatModelConfigured()) {
      return null;
    }

    final ServiceLoader<ChatModelAdapterFactory> loader =
        ServiceLoader.load(
            ChatModelAdapterFactory.class, ChatModelAdapterFactory.class.getClassLoader());
    final Iterator<ChatModelAdapterFactory> factories = loader.iterator();

    if (!factories.hasNext()) {
      throw new IllegalStateException(
          "Judge chat model properties are configured but no ChatModelAdapterFactory was found "
              + "on the classpath");
    }

    final List<String> triedFactories = new ArrayList<>();
    ChatModelAdapter adapter = null;
    while (factories.hasNext() && adapter == null) {
      final ChatModelAdapterFactory factory = factories.next();
      triedFactories.add(factory.getClass().getName());
      adapter = factory.create(rawProperties);
    }

    if (adapter == null) {
      final String provider =
          rawProperties.getProperty(
              JudgeProperties.PROPERTY_NAME_JUDGE_CHAT_MODEL_PROVIDER, "<not set>");
      throw new IllegalStateException(
          "No ChatModelAdapterFactory could create an adapter for provider '"
              + provider
              + "'. Tried: "
              + triedFactories);
    }

    return JudgeConfig.of(
        adapter, judgeProperties.getThreshold(), judgeProperties.getCustomPrompt());
  }
}
