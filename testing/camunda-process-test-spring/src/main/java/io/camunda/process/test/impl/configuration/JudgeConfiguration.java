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
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

public class JudgeConfiguration {

  private static final double DEFAULT_THRESHOLD = JudgeConfig.DEFAULT_THRESHOLD;

  private double threshold = DEFAULT_THRESHOLD;
  private String customPrompt;

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

  public ChatModelConfiguration getChatModel() {
    return chatModel;
  }

  public void setChatModel(final ChatModelConfiguration chatModel) {
    this.chatModel = chatModel;
  }

  public boolean isExplicitlyConfigured() {
    return Double.compare(threshold, DEFAULT_THRESHOLD) != 0
        || StringUtils.hasText(customPrompt)
        || StringUtils.hasText(chatModel.getProvider());
  }

  public static class ChatModelConfiguration {

    private String provider;
    private String model;
    private String apiKey;
    private String baseUrl;
    private String region;

    @NestedConfigurationProperty
    private CredentialsConfiguration credentials = new CredentialsConfiguration();

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

    public CredentialsConfiguration getCredentials() {
      return credentials;
    }

    public void setCredentials(final CredentialsConfiguration credentials) {
      this.credentials = credentials;
    }
  }

  public static class CredentialsConfiguration {

    private String accessKey;
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
