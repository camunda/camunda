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

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyMapOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;
import static io.camunda.process.test.impl.similarity.BaseProviderConfig.PROVIDER_AMAZON_BEDROCK;
import static io.camunda.process.test.impl.similarity.BaseProviderConfig.PROVIDER_AZURE_OPENAI;
import static io.camunda.process.test.impl.similarity.BaseProviderConfig.PROVIDER_OPENAI;
import static io.camunda.process.test.impl.similarity.BaseProviderConfig.PROVIDER_OPENAI_COMPATIBLE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.process.test.api.similarity.ProviderConfig;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

public class SemanticSimilarityProperties {

  public static final String PROPERTY_NAME_SIMILARITY_THRESHOLD = "similarity.threshold";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_PROVIDER =
      "similarity.embeddingModel.provider";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_MODEL =
      "similarity.embeddingModel.model";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_API_KEY =
      "similarity.embeddingModel.apiKey";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_BASE_URL =
      "similarity.embeddingModel.baseUrl";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_REGION =
      "similarity.embeddingModel.region";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_CREDENTIALS_ACCESS_KEY =
      "similarity.embeddingModel.credentials.accessKey";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_CREDENTIALS_SECRET_KEY =
      "similarity.embeddingModel.credentials.secretKey";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_ENDPOINT =
      "similarity.embeddingModel.endpoint";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_DIMENSIONS =
      "similarity.embeddingModel.dimensions";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_HEADERS =
      "similarity.embeddingModel.headers";
  public static final String PROPERTY_NAME_SIMILARITY_PREPROCESS_DEFAULTS_ENABLED =
      "similarity.preprocessors.defaults-enabled";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_NORMALIZE =
      "similarity.embeddingModel.normalize";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_TIMEOUT =
      "similarity.embeddingModel.timeout";
  public static final String PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_CUSTOM_PROPERTIES_PREFIX =
      "similarity.embeddingModel.customProperties";

  private static final double DEFAULT_THRESHOLD = SemanticSimilarityConfig.DEFAULT_THRESHOLD;

  private final double threshold;
  private final String embeddingModelProvider;
  private final String embeddingModelModel;
  private final String embeddingModelApiKey;
  private final String embeddingModelBaseUrl;
  private final String embeddingModelRegion;
  private final String embeddingModelCredentialsAccessKey;
  private final String embeddingModelCredentialsSecretKey;
  private final String embeddingModelEndpoint;
  private final Integer embeddingModelDimensions;
  private final Map<String, String> embeddingModelHeaders;
  private final boolean defaultPreprocessorsEnabled;
  private final Boolean embeddingModelNormalize;
  private final Duration embeddingModelTimeout;
  private final Map<String, String> embeddingModelCustomProperties;

  public SemanticSimilarityProperties(final Properties properties) {
    final double parsedThreshold =
        getPropertyOrDefault(
            properties, PROPERTY_NAME_SIMILARITY_THRESHOLD, Double::parseDouble, DEFAULT_THRESHOLD);
    if (parsedThreshold < 0.0 || parsedThreshold > 1.0) {
      throw new IllegalArgumentException(
          "similarity.threshold must be between 0.0 and 1.0, was: " + parsedThreshold);
    }
    threshold = parsedThreshold;

    embeddingModelProvider =
        getPropertyOrNull(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_PROVIDER);
    embeddingModelModel =
        getPropertyOrNull(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_MODEL);
    embeddingModelApiKey =
        getPropertyOrNull(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_API_KEY);
    embeddingModelBaseUrl =
        getPropertyOrNull(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_BASE_URL);
    embeddingModelRegion =
        getPropertyOrNull(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_REGION);
    embeddingModelCredentialsAccessKey =
        getPropertyOrNull(
            properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_CREDENTIALS_ACCESS_KEY);
    embeddingModelCredentialsSecretKey =
        getPropertyOrNull(
            properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_CREDENTIALS_SECRET_KEY);
    embeddingModelEndpoint =
        getPropertyOrNull(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_ENDPOINT);
    embeddingModelDimensions =
        getPropertyOrNull(
            properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_DIMENSIONS, Integer::parseInt);
    embeddingModelHeaders =
        getPropertyMapOrEmpty(properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_HEADERS);
    defaultPreprocessorsEnabled =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_SIMILARITY_PREPROCESS_DEFAULTS_ENABLED,
            Boolean::parseBoolean,
            true);
    embeddingModelNormalize =
        getPropertyOrNull(
            properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_NORMALIZE, Boolean::parseBoolean);
    embeddingModelTimeout =
        getPropertyOrNull(
            properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_TIMEOUT, Duration::parse);
    embeddingModelCustomProperties =
        getPropertyMapOrEmpty(
            properties, PROPERTY_NAME_SIMILARITY_EMBEDDING_MODEL_CUSTOM_PROPERTIES_PREFIX);
  }

  public boolean hasProviderConfigured() {
    return isNotBlank(embeddingModelProvider);
  }

  public double getThreshold() {
    return threshold;
  }

  public boolean isDefaultPreprocessorsEnabled() {
    return defaultPreprocessorsEnabled;
  }

  public ProviderConfig toProviderConfig() {
    if (embeddingModelProvider == null) {
      return null;
    }
    final String normalized = embeddingModelProvider.trim().toLowerCase();
    final BaseProviderConfig config;
    switch (normalized) {
      case PROVIDER_OPENAI:
        config = new BaseProviderConfig.OpenAiConfig(embeddingModelModel, embeddingModelApiKey);
        break;
      case PROVIDER_OPENAI_COMPATIBLE:
        config =
            new BaseProviderConfig.OpenAiCompatibleConfig(
                embeddingModelModel,
                embeddingModelBaseUrl,
                embeddingModelApiKey,
                embeddingModelHeaders);
        break;
      case PROVIDER_AZURE_OPENAI:
        config =
            new BaseProviderConfig.AzureOpenAiConfig(
                embeddingModelModel, embeddingModelEndpoint, embeddingModelApiKey);
        break;
      case PROVIDER_AMAZON_BEDROCK:
        config =
            new BaseProviderConfig.AmazonBedrockConfig(
                embeddingModelModel,
                embeddingModelRegion,
                embeddingModelApiKey,
                embeddingModelCredentialsAccessKey,
                embeddingModelCredentialsSecretKey,
                embeddingModelNormalize);
        break;
      default:
        config =
            new BaseProviderConfig.GenericConfig(
                normalized, embeddingModelModel, embeddingModelCustomProperties);
        break;
    }
    if (embeddingModelDimensions != null) {
      config.setDimensions(embeddingModelDimensions);
    }
    if (embeddingModelTimeout != null) {
      config.setTimeout(embeddingModelTimeout);
    }
    return config;
  }
}
