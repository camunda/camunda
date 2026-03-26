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
package io.camunda.process.test.impl.configuration;

import io.camunda.process.test.api.similarity.ProviderConfig;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

public class SemanticSimilarityConfiguration {

  private static final double DEFAULT_THRESHOLD = SemanticSimilarityConfig.DEFAULT_THRESHOLD;

  /** The minimum cosine similarity score (0.0–1.0) for a semantic similarity assertion to pass. */
  private double threshold = DEFAULT_THRESHOLD;

  @NestedConfigurationProperty
  private EmbeddingModelConfiguration embeddingModel = new EmbeddingModelConfiguration();

  @NestedConfigurationProperty
  private PreprocessorsConfiguration preprocessors = new PreprocessorsConfiguration();

  public double getThreshold() {
    return threshold;
  }

  public void setThreshold(final double threshold) {
    this.threshold = threshold;
  }

  public EmbeddingModelConfiguration getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(final EmbeddingModelConfiguration embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public PreprocessorsConfiguration getPreprocessors() {
    return preprocessors;
  }

  public void setPreprocessors(final PreprocessorsConfiguration preprocessors) {
    this.preprocessors = preprocessors;
  }

  public boolean hasProviderConfigured() {
    return StringUtils.hasText(embeddingModel.getProvider());
  }

  public ProviderConfig toProviderConfig() {
    final AwsCredentialsConfiguration credentials = embeddingModel.getCredentials();
    final String provider = embeddingModel.getProvider().trim().toLowerCase();
    final BaseProviderConfig config;
    switch (provider) {
      case BaseProviderConfig.PROVIDER_OPENAI:
        config =
            new BaseProviderConfig.OpenAiConfig(
                embeddingModel.getModel(),
                embeddingModel.getApiKey(),
                embeddingModel.getDimensions());
        break;
      case BaseProviderConfig.PROVIDER_OPENAI_COMPATIBLE:
        config =
            new BaseProviderConfig.OpenAiCompatibleConfig(
                embeddingModel.getModel(),
                embeddingModel.getBaseUrl(),
                embeddingModel.getApiKey(),
                embeddingModel.getDimensions(),
                embeddingModel.getHeaders());
        break;
      case BaseProviderConfig.PROVIDER_AZURE_OPENAI:
        config =
            new BaseProviderConfig.AzureOpenAiConfig(
                embeddingModel.getModel(),
                embeddingModel.getEndpoint(),
                embeddingModel.getApiKey(),
                embeddingModel.getDimensions());
        break;
      case BaseProviderConfig.PROVIDER_AMAZON_BEDROCK:
        config =
            new BaseProviderConfig.AmazonBedrockConfig(
                embeddingModel.getModel(),
                embeddingModel.getRegion(),
                embeddingModel.getApiKey(),
                credentials != null ? credentials.getAccessKey() : null,
                credentials != null ? credentials.getSecretKey() : null,
                embeddingModel.getNormalize(),
                embeddingModel.getDimensions());
        break;
      default:
        config =
            new BaseProviderConfig.GenericConfig(
                provider, embeddingModel.getModel(), embeddingModel.customProperties);
        break;
    }
    if (embeddingModel.getTimeout() != null) {
      config.setTimeout(embeddingModel.getTimeout());
    }
    return config;
  }

  public static class EmbeddingModelConfiguration {

    /**
     * The embedding model provider to use. Supported providers: openai, amazon-bedrock,
     * azure-openai, openai-compatible.
     */
    private String provider;

    /** The embedding model name (e.g. 'text-embedding-3-small', 'amazon.titan-embed-text-v2:0'). */
    private String model;

    /** The API key for authenticating with the embedding model provider. */
    private String apiKey;

    /** The base URL for the embedding model API. Required for the 'openai-compatible' provider. */
    private String baseUrl;

    /** The AWS region for the Amazon Bedrock provider (e.g. 'us-east-1'). */
    private String region;

    /** The Azure endpoint URL for the Azure OpenAI provider. */
    private String endpoint;

    /** The number of dimensions for the embedding vectors (optional, provider-dependent). */
    private Integer dimensions;

    /** Whether to normalize the embedding vectors (optional, Amazon Bedrock Titan only). */
    private Boolean normalize;

    /** Optional custom HTTP headers to include in embedding model requests. */
    private Map<String, String> headers;

    /** The timeout for embedding model API calls as an ISO-8601 duration (e.g. 'PT30S', 'PT2M'). */
    private Duration timeout;

    @NestedConfigurationProperty
    private AwsCredentialsConfiguration credentials = new AwsCredentialsConfiguration();

    /** Custom properties for custom/unknown providers. */
    private Map<String, String> customProperties = Collections.emptyMap();

    public String getProvider() {
      return provider;
    }

    public void setProvider(final String provider) {
      this.provider = provider;
    }

    public String getModel() {
      return model;
    }

    public void setModel(final String model) {
      this.model = model;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(final String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(final String region) {
      this.region = region;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(final String endpoint) {
      this.endpoint = endpoint;
    }

    public Integer getDimensions() {
      return dimensions;
    }

    public void setDimensions(final Integer dimensions) {
      this.dimensions = dimensions;
    }

    public Boolean getNormalize() {
      return normalize;
    }

    public void setNormalize(final Boolean normalize) {
      this.normalize = normalize;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    public void setHeaders(final Map<String, String> headers) {
      this.headers = headers;
    }

    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(final Duration timeout) {
      this.timeout = timeout;
    }

    public AwsCredentialsConfiguration getCredentials() {
      return credentials;
    }

    public void setCredentials(final AwsCredentialsConfiguration credentials) {
      this.credentials = credentials;
    }

    public Map<String, String> getCustomProperties() {
      return customProperties;
    }

    public void setCustomProperties(final Map<String, String> customProperties) {
      this.customProperties = customProperties;
    }
  }

  public static class AwsCredentialsConfiguration {

    /** The AWS access key for authenticating with Amazon Bedrock. */
    private String accessKey;

    /** The AWS secret key for authenticating with Amazon Bedrock. */
    private String secretKey;

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(final String accessKey) {
      this.accessKey = accessKey;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(final String secretKey) {
      this.secretKey = secretKey;
    }
  }

  public static class PreprocessorsConfiguration {

    private boolean defaultsEnabled = true;

    public boolean isDefaultsEnabled() {
      return defaultsEnabled;
    }

    public void setDefaultsEnabled(final boolean defaultsEnabled) {
      this.defaultsEnabled = defaultsEnabled;
    }
  }
}
