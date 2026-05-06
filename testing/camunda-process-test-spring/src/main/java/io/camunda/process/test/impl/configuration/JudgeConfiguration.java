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

import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.ProviderConfig;
import io.camunda.process.test.impl.judge.BaseProviderConfig;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

public class JudgeConfiguration {

  private static final double DEFAULT_THRESHOLD = JudgeConfig.DEFAULT_THRESHOLD;
  private static final boolean DEFAULT_RESOLVE_DOCUMENTS = JudgeConfig.DEFAULT_RESOLVE_DOCUMENTS;

  /** The confidence threshold for the AI judge to consider an assertion as passed. */
  private double threshold = DEFAULT_THRESHOLD;

  /** A custom prompt to use for the AI judge instead of the default one. */
  private String customPrompt;

  /**
   * Whether Camunda document references found in the asserted variable should be resolved and their
   * content provided to the judge as additional context. Disabled by default to avoid unnecessary
   * token cost.
   */
  private boolean resolveDocuments = DEFAULT_RESOLVE_DOCUMENTS;

  @NestedConfigurationProperty
  private ChatModelConfiguration chatModel = new ChatModelConfiguration();

  public double getThreshold() {
    return threshold;
  }

  public void setThreshold(final double threshold) {
    this.threshold = threshold;
  }

  public String getCustomPrompt() {
    return customPrompt;
  }

  public void setCustomPrompt(final String customPrompt) {
    this.customPrompt = customPrompt;
  }

  public boolean isResolveDocuments() {
    return resolveDocuments;
  }

  public void setResolveDocuments(final boolean resolveDocuments) {
    this.resolveDocuments = resolveDocuments;
  }

  public ChatModelConfiguration getChatModel() {
    return chatModel;
  }

  public void setChatModel(final ChatModelConfiguration chatModel) {
    this.chatModel = chatModel;
  }

  public boolean hasProviderConfigured() {
    return StringUtils.hasText(chatModel.getProvider());
  }

  public ProviderConfig toProviderConfig() {
    final AwsCredentialsConfiguration credentials = chatModel.getCredentials();
    final String provider = chatModel.getProvider().trim().toLowerCase();
    final BaseProviderConfig config;
    switch (provider) {
      case BaseProviderConfig.PROVIDER_OPENAI:
        config = new BaseProviderConfig.OpenAiConfig(chatModel.getModel(), chatModel.getApiKey());
        break;
      case BaseProviderConfig.PROVIDER_ANTHROPIC:
        config =
            new BaseProviderConfig.AnthropicConfig(chatModel.getModel(), chatModel.getApiKey());
        break;
      case BaseProviderConfig.PROVIDER_AMAZON_BEDROCK:
        config =
            new BaseProviderConfig.AmazonBedrockConfig(
                chatModel.getModel(),
                chatModel.getRegion(),
                chatModel.getApiKey(),
                credentials != null ? credentials.getAccessKey() : null,
                credentials != null ? credentials.getSecretKey() : null);
        break;
      case BaseProviderConfig.PROVIDER_OPENAI_COMPATIBLE:
        config =
            new BaseProviderConfig.OpenAiCompatibleConfig(
                chatModel.getModel(),
                chatModel.getBaseUrl(),
                chatModel.getApiKey(),
                chatModel.getHeaders());
        break;
      case BaseProviderConfig.PROVIDER_AZURE_OPENAI:
        config =
            new BaseProviderConfig.AzureOpenAiConfig(
                chatModel.getModel(), chatModel.getEndpoint(), chatModel.getApiKey());
        break;
      default:
        config =
            new BaseProviderConfig.GenericConfig(
                provider, chatModel.getModel(), chatModel.getCustomProperties());
        break;
    }
    if (chatModel.getTimeout() != null) {
      config.setTimeout(chatModel.getTimeout());
    }
    if (chatModel.getTemperature() != null) {
      config.setTemperature(chatModel.getTemperature());
    }
    return config;
  }

  public static class ChatModelConfiguration {

    /**
     * The LLM provider to use for the AI judge. Supported providers: openai, anthropic,
     * amazon-bedrock, openai-compatible, azure-openai.
     */
    private String provider;

    /**
     * The model name to use for the AI judge (e.g. 'gpt-4o', 'claude-sonnet-4-20250514',
     * 'us.anthropic.claude-sonnet-4-20250514-v1:0').
     */
    private String model;

    /** The API key for authenticating with the chat model provider. */
    private String apiKey;

    /** The base URL for the chat model API. Required for the 'openai-compatible' provider. */
    private String baseUrl;

    /**
     * The Azure OpenAI resource endpoint URL. Required for the 'azure-openai' provider (e.g.
     * 'https://my-resource.openai.azure.com/').
     */
    private String endpoint;

    /** The AWS region for the Amazon Bedrock provider (e.g. 'us-east-1'). */
    private String region;

    /** Optional custom HTTP headers to include in chat model requests. */
    private Map<String, String> headers;

    /** The timeout for LLM API calls as an ISO-8601 duration (e.g. 'PT30S', 'PT2M'). */
    private Duration timeout;

    /** The temperature for LLM API calls (e.g. 0.7). */
    private Double temperature;

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

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(final String endpoint) {
      this.endpoint = endpoint;
    }

    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(final Duration timeout) {
      this.timeout = timeout;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(final Double temperature) {
      this.temperature = temperature;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(final String region) {
      this.region = region;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    public void setHeaders(final Map<String, String> headers) {
      this.headers = headers;
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
}
