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

/** Base class for provider-specific configuration. */
public abstract class ProviderConfig {

  public static final String PROVIDER_OPENAI = "openai";
  public static final String PROVIDER_ANTHROPIC = "anthropic";
  public static final String PROVIDER_AMAZON_BEDROCK = "amazon-bedrock";
  public static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";

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

  /** OpenAI provider configuration. */
  public static final class OpenAiConfig extends ProviderConfig {

    private final String apiKey;

    public OpenAiConfig(final String model, final String apiKey) {
      super(PROVIDER_OPENAI, model);
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
      super(PROVIDER_ANTHROPIC, model);
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
      super(PROVIDER_AMAZON_BEDROCK, model);
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
      super(PROVIDER_OPENAI_COMPATIBLE, model);
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
