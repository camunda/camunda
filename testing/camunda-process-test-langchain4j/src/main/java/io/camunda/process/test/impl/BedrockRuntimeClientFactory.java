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
package io.camunda.process.test.impl;

import static io.camunda.process.test.impl.ModelBuilderSupport.hasText;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.scheme.NoAuthAuthScheme;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

/** Factory for creating a configured {@link BedrockRuntimeClient} from Amazon Bedrock config. */
public final class BedrockRuntimeClientFactory {

  // reflects the default timeout used by BedrockChatModel
  public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);

  private static final Logger LOG = LoggerFactory.getLogger(BedrockRuntimeClientFactory.class);

  private BedrockRuntimeClientFactory() {}

  public static BedrockRuntimeClient build(
      final String region,
      final String apiKey,
      final String credentialsAccessKey,
      final String credentialsSecretKey,
      final Duration timeout) {
    final boolean hasAccessKey = hasText(credentialsAccessKey);
    final boolean hasSecretKey = hasText(credentialsSecretKey);
    final boolean hasKeyPairAuth = hasAccessKey && hasSecretKey;
    final boolean hasPartialKeyPair = hasAccessKey != hasSecretKey;
    final boolean hasApiKeyAuth = hasText(apiKey);
    final Duration effectiveTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;

    if (hasPartialKeyPair) {
      throw new IllegalStateException(
          "Incomplete key-pair authentication for the 'amazon-bedrock' provider: "
              + "both 'accessKey' and 'secretKey' must be set together.");
    }

    if (hasKeyPairAuth && hasApiKeyAuth) {
      throw new IllegalStateException(
          "Ambiguous authentication for the 'amazon-bedrock' provider: "
              + "both accessKey/secretKey and apiKey are set. Use only one authentication method.");
    }

    final BedrockRuntimeClientBuilder clientBuilder = BedrockRuntimeClient.builder();

    if (hasText(region)) {
      LOG.debug("Using configured region '{}'", region.trim());
      clientBuilder.region(Region.of(region.trim()));
    } else {
      LOG.debug("No region configured, falling back to the default region");
    }

    if (hasKeyPairAuth) {
      LOG.debug("Using access key / secret key authentication");
      clientBuilder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(
                  credentialsAccessKey.trim(), credentialsSecretKey.trim())));
    } else if (hasApiKeyAuth) {
      LOG.debug("Using API key (Bearer token) authentication");
      clientBuilder
          .credentialsProvider(AnonymousCredentialsProvider.create())
          .putAuthScheme(NoAuthAuthScheme.create());
    }

    LOG.debug("Setting timeout to {}", effectiveTimeout);
    clientBuilder.overrideConfiguration(
        cfg -> {
          if (hasApiKeyAuth) {
            cfg.headers(Map.of("Authorization", List.of("Bearer " + apiKey.trim())));
          }
          cfg.apiCallTimeout(effectiveTimeout);
        });

    return clientBuilder.build();
  }
}
