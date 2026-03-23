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

import static io.camunda.process.test.impl.judge.BaseProviderConfig.PROVIDER_AMAZON_BEDROCK;
import static io.camunda.process.test.impl.judge.BaseProviderConfig.PROVIDER_ANTHROPIC;
import static io.camunda.process.test.impl.judge.BaseProviderConfig.PROVIDER_AZURE_OPENAI;
import static io.camunda.process.test.impl.judge.BaseProviderConfig.PROVIDER_OPENAI;
import static io.camunda.process.test.impl.judge.BaseProviderConfig.PROVIDER_OPENAI_COMPATIBLE;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyMapOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.ProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
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
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_ENDPOINT = "judge.chatModel.endpoint";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_HEADERS = "judge.chatModel.headers";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_TIMEOUT = "judge.chatModel.timeout";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_TEMPERATURE =
      "judge.chatModel.temperature";
  public static final String PROPERTY_NAME_JUDGE_CHAT_MODEL_CUSTOM_PROPERTIES_PREFIX =
      "judge.chatModel.customProperties";

  private static final double DEFAULT_THRESHOLD = JudgeConfig.DEFAULT_THRESHOLD;

  private final double threshold;
  private final String customPrompt;
  private final String chatModelProvider;
  private final String chatModelModel;
  private final String chatModelApiKey;
  private final String chatModelBaseUrl;
  private final String chatModelRegion;
  private final String chatModelEndpoint;
  private final Map<String, String> chatModelHeaders;
  private final Duration chatModelTimeout;
  private final Double chatModelTemperature;
  private final String chatModelCredentialsAccessKey;
  private final String chatModelCredentialsSecretKey;
  private final Map<String, String> chatModelCustomProperties;

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
    chatModelEndpoint = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_ENDPOINT);
    chatModelHeaders = getPropertyMapOrEmpty(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_HEADERS);
    chatModelTimeout = parseTimeout(properties);
    chatModelTemperature =
        getPropertyOrNull(
            properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_TEMPERATURE, Double::parseDouble);
    chatModelCredentialsAccessKey =
        getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_CREDENTIALS_ACCESS_KEY);
    chatModelCredentialsSecretKey =
        getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_CREDENTIALS_SECRET_KEY);
    chatModelCustomProperties =
        getPropertyMapOrEmpty(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_CUSTOM_PROPERTIES_PREFIX);
  }

  public boolean hasProviderConfigured() {
    return isNotBlank(chatModelProvider);
  }

  public double getThreshold() {
    return threshold;
  }

  public String getCustomPrompt() {
    return customPrompt;
  }

  public ProviderConfig toProviderConfig() {
    if (chatModelProvider == null) {
      return null;
    }
    final String normalized = chatModelProvider.trim().toLowerCase();
    final BaseProviderConfig config;
    switch (normalized) {
      case PROVIDER_OPENAI:
        config = new BaseProviderConfig.OpenAiConfig(chatModelModel, chatModelApiKey);
        break;
      case PROVIDER_ANTHROPIC:
        config = new BaseProviderConfig.AnthropicConfig(chatModelModel, chatModelApiKey);
        break;
      case PROVIDER_AMAZON_BEDROCK:
        config =
            new BaseProviderConfig.AmazonBedrockConfig(
                chatModelModel,
                chatModelRegion,
                chatModelApiKey,
                chatModelCredentialsAccessKey,
                chatModelCredentialsSecretKey);
        break;
      case PROVIDER_OPENAI_COMPATIBLE:
        config =
            new BaseProviderConfig.OpenAiCompatibleConfig(
                chatModelModel, chatModelBaseUrl, chatModelApiKey, chatModelHeaders);
        break;
      case PROVIDER_AZURE_OPENAI:
        config =
            new BaseProviderConfig.AzureOpenAiConfig(
                chatModelModel, chatModelEndpoint, chatModelApiKey);
        break;
      default:
        config =
            new BaseProviderConfig.GenericConfig(
                normalized, chatModelModel, chatModelCustomProperties);
        break;
    }
    if (chatModelTimeout != null) {
      config.setTimeout(chatModelTimeout);
    }
    if (chatModelTemperature != null) {
      config.setTemperature(chatModelTemperature);
    }
    return config;
  }

  private Duration parseTimeout(final Properties properties) {
    final String raw = getPropertyOrNull(properties, PROPERTY_NAME_JUDGE_CHAT_MODEL_TIMEOUT);
    if (raw == null) {
      return null;
    }
    try {
      return Duration.parse(raw);
    } catch (final DateTimeParseException e) {
      throw new IllegalArgumentException(
          "judge.chatModel.timeout must be a valid ISO-8601 duration (e.g. PT30S), was: " + raw, e);
    }
  }
}
