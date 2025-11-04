/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.PutClusterSettingsRequest;
import org.opensearch.client.opensearch.cluster.PutClusterSettingsRequest.Builder;
import org.opensearch.client.opensearch.cluster.PutClusterSettingsResponse;
import org.opensearch.client.opensearch.core.InfoResponse;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexTemplateRequest;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

public class AWSOpenSearchSetupHelper implements MultiDbSetupHelper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(AWSOpenSearchSetupHelper.class);
  private static final Duration DEFAULT_INDICES_LIFECYCLE_POLL_INTERVAL = Duration.ofMinutes(5);

  private final AtomicBoolean hasClusterSettingsChanged = new AtomicBoolean(false);
  private final SdkHttpClient httpClient = ApacheHttpClient.builder().build();
  private final OpenSearchClient client;

  private final Collection<IndexDescriptor> expectedDescriptors;

  public AWSOpenSearchSetupHelper(
      final String endpoint, final Collection<IndexDescriptor> expectedDescriptors) {
    final URI uri = URI.create(endpoint);
    this.expectedDescriptors = expectedDescriptors;
    final var region = new DefaultAwsRegionProviderChain().getRegion();
    client =
        new OpenSearchClient(
            new AwsSdk2Transport(
                httpClient,
                uri.getHost(),
                region,
                AwsSdk2TransportOptions.builder()
                    .setMapper(new JacksonJsonpMapper(OBJECT_MAPPER))
                    .build()));
  }

  @Override
  public boolean validateConnection() {
    try {
      final InfoResponse info = client.info();
      return StringUtils.isNotBlank(info.clusterName());
    } catch (final IOException e) {
      LOGGER.debug("Exception on validating exception", e);
    }
    return false;
  }

  @Override
  public void applyIndexPoliciesPollInterval(final Duration pollInterval) {
    applyIndicesLifecyclePollInterval(pollInterval);
    hasClusterSettingsChanged.set(true); // mark cluster settings changed to reset after the test
  }

  @Override
  public boolean validateSchemaCreation(final String testPrefix) {
    try {
      final int count = getCountOfIndicesWithPrefix(testPrefix);
      if (expectedDescriptors.size() > count) {
        LOGGER.debug(
            "[{}/{}] indices with prefix {} in secondary storage, retry...",
            count,
            expectedDescriptors.size(),
            testPrefix);
        return false;
      }

      final int templateCount = getCountOfIndexTemplatesWithPrefix(testPrefix);
      if (templateCount <= 0) {
        LOGGER.debug("{} templates found for prefix {}, retry...", templateCount, testPrefix);
        return false;
      }

      LOGGER.debug(
          "Found {} indices and {} index templates. Schema creation validated.",
          count,
          templateCount);
      return true;
    } catch (final IOException | InterruptedException e) {
      LOGGER.debug("Exception on retrieving schema with prefix {}", testPrefix, e);
    }
    return false;
  }

  @Override
  public void cleanup(final String prefix) {
    // reset cluster settings if changed
    resetIndicesLifecyclePollInterval();

    try {
      client.indices().delete(new DeleteIndexRequest.Builder().index(prefix + "*").build());
    } catch (final IOException e) {
      LOGGER.warn("Exception on cleaning indexes {}", prefix, e);
    }

    try {
      client
          .indices()
          .deleteIndexTemplate(new DeleteIndexTemplateRequest.Builder().name(prefix + "*").build());
    } catch (final IOException e) {
      LOGGER.warn("Exception on cleaning index templates {}", prefix, e);
    }
  }

  @Override
  public void close() throws Exception {
    httpClient.close();
  }

  protected int getCountOfIndicesWithPrefix(final String testPrefix)
      throws IOException, InterruptedException {
    return client
        .indices()
        .get(new GetIndexRequest.Builder().index(testPrefix + "*").build())
        .result()
        .size();
  }

  protected int getCountOfIndexTemplatesWithPrefix(final String testPrefix)
      throws IOException, InterruptedException {
    return client
        .indices()
        .getIndexTemplate(new GetIndexTemplateRequest.Builder().name(testPrefix + "*").build())
        .indexTemplates()
        .size();
  }

  private void resetIndicesLifecyclePollInterval() {
    if (hasClusterSettingsChanged.getAndSet(false)) {
      applyIndicesLifecyclePollInterval(DEFAULT_INDICES_LIFECYCLE_POLL_INTERVAL);
    }
  }

  private void applyIndicesLifecyclePollInterval(final Duration pollInterval) {
    final Map<String, JsonData> settings =
        Map.of(
            "persistent",
            JsonData.of(
                Map.of(
                    "plugins.index_state_management.job_interval",
                    String.format("%d", pollInterval.toMinutes()))));

    applyClusterSettings(settings);
  }

  private void applyClusterSettings(final Map<String, JsonData> settings) {
    withRetry(
        () -> {
          final PutClusterSettingsRequest build = new Builder().persistent(settings).build();

          final PutClusterSettingsResponse putSettingsResponse =
              client.cluster().putSettings(build);

          if (putSettingsResponse.acknowledged()) {
            LOGGER.info("Applied cluster settings successfully");
          } else {
            LOGGER.warn("Failed to apply cluster settings, retrying...");
          }

          return putSettingsResponse.acknowledged();
        },
        5,
        LOGGER);
  }
}
