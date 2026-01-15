/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
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

public class AWSOpenSearchSetupHelper extends OpenSearchSetupHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(AWSOpenSearchSetupHelper.class);
  private final SdkHttpClient httpClient = ApacheHttpClient.builder().build();
  private final OpenSearchClient client;

  public AWSOpenSearchSetupHelper(
      final String endpoint, final Collection<IndexDescriptor> expectedDescriptors) {
    super(endpoint, expectedDescriptors);
    final URI uri = URI.create(endpoint);
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
  public void close() {
    httpClient.close();
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
  protected void applyClusterSettings(final Map<String, Object> settings) {
    withRetry(
        () -> {
          final Map<String, JsonData> mappedSettings =
              settings.entrySet().stream()
                  .map(s -> new SimpleEntry<>(s.getKey(), JsonData.of(s.getValue())))
                  .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

          final PutClusterSettingsRequest build = new Builder().persistent(mappedSettings).build();

          final PutClusterSettingsResponse putSettingsResponse =
              client.cluster().putSettings(build);

          if (putSettingsResponse.acknowledged()) {
            LOGGER.info("Applied cluster settings successfully");
          } else {
            LOGGER.warn("Failed to apply cluster settings, retrying...");
          }

          return putSettingsResponse.acknowledged();
        },
        5);
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
}
