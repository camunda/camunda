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

/** Typed configuration data passed to a {@link JudgeConfigBootstrapProvider}. */
public final class JudgeConfigBootstrapData {

  private final double threshold;
  private final String customPrompt;
  private final ProviderConfig providerConfig;

  private JudgeConfigBootstrapData(final Builder builder) {
    threshold = builder.threshold;
    customPrompt = builder.customPrompt;
    providerConfig = builder.providerConfig;
  }

  public double getThreshold() {
    return threshold;
  }

  public String getCustomPrompt() {
    return customPrompt;
  }

  public ProviderConfig getProviderConfig() {
    return providerConfig;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private double threshold = JudgeConfig.DEFAULT_THRESHOLD;
    private String customPrompt;
    private ProviderConfig providerConfig;

    public Builder threshold(final double threshold) {
      this.threshold = threshold;
      return this;
    }

    public Builder customPrompt(final String customPrompt) {
      this.customPrompt = customPrompt;
      return this;
    }

    public Builder providerConfig(final ProviderConfig providerConfig) {
      this.providerConfig = providerConfig;
      return this;
    }

    public JudgeConfigBootstrapData build() {
      return new JudgeConfigBootstrapData(this);
    }
  }

  /** Base class for provider-specific configuration. */
  public abstract static class ProviderConfig {

    private final String provider;
    private final String model;

    protected ProviderConfig(final String provider, final String model) {
      this.provider = provider;
      this.model = model;
    }

    public String getProvider() {
      return provider;
    }

    public String getModel() {
      return model;
    }
  }

  /** OpenAI provider configuration. */
  public static final class OpenAiConfig extends ProviderConfig {

    private final String apiKey;

    public OpenAiConfig(final String model, final String apiKey) {
      super("openai", model);
      this.apiKey = apiKey;
    }

    public String getApiKey() {
      return apiKey;
    }
  }

  /** Anthropic provider configuration. */
  public static final class AnthropicConfig extends ProviderConfig {

    private final String apiKey;

    public AnthropicConfig(final String model, final String apiKey) {
      super("anthropic", model);
      this.apiKey = apiKey;
    }

    public String getApiKey() {
      return apiKey;
    }
  }

  /** Amazon Bedrock provider configuration. */
  public static final class AmazonBedrockConfig extends ProviderConfig {

    private final String region;
    private final String apiKey;
    private final String credentialsAccessKey;
    private final String credentialsSecretKey;

    public AmazonBedrockConfig(
        final String model,
        final String region,
        final String apiKey,
        final String credentialsAccessKey,
        final String credentialsSecretKey) {
      super("amazon-bedrock", model);
      this.region = region;
      this.apiKey = apiKey;
      this.credentialsAccessKey = credentialsAccessKey;
      this.credentialsSecretKey = credentialsSecretKey;
    }

    public String getRegion() {
      return region;
    }

    public String getApiKey() {
      return apiKey;
    }

    public String getCredentialsAccessKey() {
      return credentialsAccessKey;
    }

    public String getCredentialsSecretKey() {
      return credentialsSecretKey;
    }
  }

  /** OpenAI-compatible provider configuration. */
  public static final class OpenAiCompatibleConfig extends ProviderConfig {

    private final String baseUrl;
    private final String apiKey;

    public OpenAiCompatibleConfig(final String model, final String baseUrl, final String apiKey) {
      super("openai-compatible", model);
      this.baseUrl = baseUrl;
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }
  }
}
