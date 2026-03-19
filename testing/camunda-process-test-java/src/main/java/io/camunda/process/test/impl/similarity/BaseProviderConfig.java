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
package io.camunda.process.test.impl.similarity;

import io.camunda.process.test.api.similarity.ProviderConfig;
import java.util.Collections;
import java.util.Map;

/** Abstract base class for provider-specific embedding model configuration. */
public abstract class BaseProviderConfig implements ProviderConfig {

  public static final String PROVIDER_OPENAI = "openai";
  public static final String PROVIDER_AZURE_OPENAI = "azure-openai";
  public static final String PROVIDER_AMAZON_BEDROCK = "amazon-bedrock";
  public static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";

  private final String provider;
  private final String model;

  protected BaseProviderConfig(final String provider, final String model) {
    this.provider = provider;
    this.model = model;
  }

  @Override
  public String getProvider() {
    return provider;
  }

  @Override
  public String getModel() {
    return model;
  }

  /** Generic provider configuration for unknown or custom providers. */
  public static final class GenericConfig extends BaseProviderConfig {

    private final Map<String, String> customProperties;

    public GenericConfig(final String provider, final String model) {
      this(provider, model, Collections.emptyMap());
    }

    public GenericConfig(
        final String provider, final String model, final Map<String, String> customProperties) {
      super(provider, model);
      this.customProperties = Collections.unmodifiableMap(customProperties);
    }

    @Override
    public Map<String, String> getCustomProperties() {
      return customProperties;
    }
  }

  /** OpenAI embedding model provider configuration. */
  public static final class OpenAiConfig extends BaseProviderConfig {

    private final String apiKey;
    private final Integer dimensions;
    private final Map<String, String> headers;

    public OpenAiConfig(
        final String model,
        final String apiKey,
        final Integer dimensions,
        final Map<String, String> headers) {
      super(PROVIDER_OPENAI, model);
      this.apiKey = apiKey;
      this.dimensions = dimensions;
      this.headers = headers;
    }

    public String getApiKey() {
      return apiKey;
    }

    public Integer getDimensions() {
      return dimensions;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }
  }

  /** OpenAI-compatible embedding model provider configuration. */
  public static final class OpenAiCompatibleConfig extends BaseProviderConfig {

    private final String baseUrl;
    private final String apiKey;
    private final Integer dimensions;
    private final Map<String, String> headers;

    public OpenAiCompatibleConfig(
        final String model,
        final String baseUrl,
        final String apiKey,
        final Integer dimensions,
        final Map<String, String> headers) {
      super(PROVIDER_OPENAI_COMPATIBLE, model);
      this.baseUrl = baseUrl;
      this.apiKey = apiKey;
      this.dimensions = dimensions;
      this.headers = headers;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public Integer getDimensions() {
      return dimensions;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }
  }

  /** Azure OpenAI embedding model provider configuration. */
  public static final class AzureOpenAiConfig extends BaseProviderConfig {

    private final String endpoint;
    private final String apiKey;
    private final Integer dimensions;
    private final Map<String, String> headers;

    public AzureOpenAiConfig(
        final String model,
        final String endpoint,
        final String apiKey,
        final Integer dimensions,
        final Map<String, String> headers) {
      super(PROVIDER_AZURE_OPENAI, model);
      this.endpoint = endpoint;
      this.apiKey = apiKey;
      this.dimensions = dimensions;
      this.headers = headers;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public String getApiKey() {
      return apiKey;
    }

    public Integer getDimensions() {
      return dimensions;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }
  }

  /** Amazon Bedrock embedding model provider configuration. */
  public static final class AmazonBedrockConfig extends BaseProviderConfig {

    private final String region;
    private final String credentialsAccessKey;
    private final String credentialsSecretKey;
    private final Boolean normalize;
    private final Integer dimensions;

    public AmazonBedrockConfig(
        final String model,
        final String region,
        final String credentialsAccessKey,
        final String credentialsSecretKey,
        final Boolean normalize,
        final Integer dimensions) {
      super(PROVIDER_AMAZON_BEDROCK, model);
      this.region = region;
      this.credentialsAccessKey = credentialsAccessKey;
      this.credentialsSecretKey = credentialsSecretKey;
      this.normalize = normalize;
      this.dimensions = dimensions;
    }

    public String getRegion() {
      return region;
    }

    public String getCredentialsAccessKey() {
      return credentialsAccessKey;
    }

    public String getCredentialsSecretKey() {
      return credentialsSecretKey;
    }

    /**
     * Returns whether to normalize the embedding vector, or {@code null} if not set.
     *
     * @return whether to normalize, or {@code null}
     */
    public Boolean getNormalize() {
      return normalize;
    }

    public Integer getDimensions() {
      return dimensions;
    }
  }
}
