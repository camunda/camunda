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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData;
import java.util.Properties;

public class JudgeProperties {

  public static final String PROPERTY_NAME_JUDGE_THRESHOLD = "judge.threshold";
  public static final String PROPERTY_NAME_JUDGE_CUSTOM_PROMPT = "judge.customPrompt";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_PROVIDER = "judge.chatModel.provider";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_MODEL = "judge.chatModel.model";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_API_KEY = "judge.chatModel.apiKey";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_BASE_URL = "judge.chatModel.baseUrl";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_REGION = "judge.chatModel.region";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_CREDENTIALS_ACCESS_KEY =
      "judge.chatModel.credentials.accessKey";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_CREDENTIALS_SECRET_KEY =
      "judge.chatModel.credentials.secretKey";

  private static final double DEFAULT_THRESHOLD = JudgeConfig.DEFAULT_THRESHOLD;

  private final double threshold;
  private final String customPrompt;
  private final String chatModelProvider;
  private final String chatModelModel;
  private final String chatModelApiKey;
  private final String chatModelBaseUrl;
  private final String chatModelRegion;
  private final String chatModelCredentialsAccessKey;
  private final String chatModelCredentialsSecretKey;

  public JudgeProperties(final Properties properties) {
    final double parsedThreshold =
        getPropertyOrDefault(
            properties, PROPERTY_NAME_JUDGE_THRESHOLD, Double::parseDouble, DEFAULT_THRESHOLD);
    if (parsedThreshold < 0.0 || parsedThreshold > 1.0) {
      throw new IllegalArgumentException(
          "judge.threshold must be between 0.0 and 1.0, was: " + parsedThreshold);
    }
    threshold = parsedThreshold;

    customPrompt = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CUSTOM_PROMPT);
    chatModelProvider = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_PROVIDER);
    chatModelModel = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_MODEL);
    chatModelApiKey = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_API_KEY);
    chatModelBaseUrl = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_BASE_URL);
    chatModelRegion = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_REGION);
    chatModelCredentialsAccessKey =
        getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_CREDENTIALS_ACCESS_KEY);
    chatModelCredentialsSecretKey =
        getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_CREDENTIALS_SECRET_KEY);
  }

  public boolean isExplicitlyConfigured() {
    return Double.compare(threshold, DEFAULT_THRESHOLD) != 0
        || hasText(customPrompt)
        || hasText(chatModelProvider);
  }

  private boolean hasText(final String text) {
    return text != null && !text.trim().isEmpty();
  }

  public double getThreshold() {
    return threshold;
  }

  public String getCustomPrompt() {
    return customPrompt;
  }

  public boolean isChatModelConfigured() {
    return chatModelProvider != null;
  }

  public String getChatModelProvider() {
    return chatModelProvider;
  }

  public String getChatModelModel() {
    return chatModelModel;
  }

  public String getChatModelApiKey() {
    return chatModelApiKey;
  }

  public String getChatModelBaseUrl() {
    return chatModelBaseUrl;
  }

  public String getChatModelRegion() {
    return chatModelRegion;
  }

  public String getChatModelCredentialsAccessKey() {
    return chatModelCredentialsAccessKey;
  }

  public String getChatModelCredentialsSecretKey() {
    return chatModelCredentialsSecretKey;
  }

  public JudgeConfigBootstrapData toJudgeConfigurationData() {
    return JudgeConfigBootstrapData.builder()
        .provider(chatModelProvider)
        .model(chatModelModel)
        .apiKey(chatModelApiKey)
        .baseUrl(chatModelBaseUrl)
        .region(chatModelRegion)
        .credentialsAccessKey(chatModelCredentialsAccessKey)
        .credentialsSecretKey(chatModelCredentialsSecretKey)
        .threshold(threshold)
        .customPrompt(customPrompt)
        .build();
  }
}
