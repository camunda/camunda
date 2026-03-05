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
public final class JudgeConfigurationData {

  private final String provider;
  private final String model;
  private final String apiKey;
  private final String baseUrl;
  private final String region;
  private final String credentialsAccessKey;
  private final String credentialsSecretKey;
  private final double threshold;
  private final String customPrompt;

  private JudgeConfigurationData(final Builder builder) {
    provider = builder.provider;
    model = builder.model;
    apiKey = builder.apiKey;
    baseUrl = builder.baseUrl;
    region = builder.region;
    credentialsAccessKey = builder.credentialsAccessKey;
    credentialsSecretKey = builder.credentialsSecretKey;
    threshold = builder.threshold;
    customPrompt = builder.customPrompt;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
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

  public double getThreshold() {
    return threshold;
  }

  public String getCustomPrompt() {
    return customPrompt;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String provider;
    private String model;
    private String apiKey;
    private String baseUrl;
    private String region;
    private String credentialsAccessKey;
    private String credentialsSecretKey;
    private double threshold = JudgeConfig.DEFAULT_THRESHOLD;
    private String customPrompt;

    public Builder provider(final String provider) {
      this.provider = provider;
      return this;
    }

    public Builder model(final String model) {
      this.model = model;
      return this;
    }

    public Builder apiKey(final String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public Builder baseUrl(final String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder region(final String region) {
      this.region = region;
      return this;
    }

    public Builder credentialsAccessKey(final String credentialsAccessKey) {
      this.credentialsAccessKey = credentialsAccessKey;
      return this;
    }

    public Builder credentialsSecretKey(final String credentialsSecretKey) {
      this.credentialsSecretKey = credentialsSecretKey;
      return this;
    }

    public Builder threshold(final double threshold) {
      this.threshold = threshold;
      return this;
    }

    public Builder customPrompt(final String customPrompt) {
      this.customPrompt = customPrompt;
      return this;
    }

    public JudgeConfigurationData build() {
      return new JudgeConfigurationData(this);
    }
  }
}
