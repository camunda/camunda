/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.service.db.es.schema.index.AgentDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.AgentRunIndexES;
import io.camunda.optimize.service.db.os.schema.index.AgentDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.AgentRunIndexOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AgentIndexShardSettingsTest {

  private static final ConfigurationService DEFAULT_CONFIGURATION =
      ConfigurationServiceBuilder.createDefaultConfiguration();

  @Test
  void shouldCreateTheAgentRunIndexWithFiveShardsByDefault() throws IOException {
    // when
    final String elasticsearchShards =
        elasticsearchShards(new AgentRunIndexES(), DEFAULT_CONFIGURATION);
    final String openSearchShards = openSearchShards(new AgentRunIndexOS(), DEFAULT_CONFIGURATION);

    // then
    assertThat(elasticsearchShards).isEqualTo("3");
    assertThat(openSearchShards).isEqualTo("3");
  }

  @Test
  void shouldCreateTheAgentRunIndexWithTheConfiguredShards() throws IOException {
    // given
    final ConfigurationService configuration =
        ConfigurationServiceBuilder.createConfigurationWithDefaultAndAdditionalLocations(
            "config-samples/agent-instance-shards.yaml");

    // when
    final String elasticsearchShards = elasticsearchShards(new AgentRunIndexES(), configuration);
    final String openSearchShards = openSearchShards(new AgentRunIndexOS(), configuration);

    // then
    assertThat(elasticsearchShards).isEqualTo("7");
    assertThat(openSearchShards).isEqualTo("7");
  }

  @Test
  void shouldKeepTheAgentDefinitionIndexAtOneShard() throws IOException {
    // when
    final String elasticsearchShards =
        elasticsearchShards(new AgentDefinitionIndexES(), DEFAULT_CONFIGURATION);
    final String openSearchShards =
        openSearchShards(new AgentDefinitionIndexOS(), DEFAULT_CONFIGURATION);

    // then the agent run shard setting does not apply to the small definition index
    assertThat(elasticsearchShards).isEqualTo("1");
    assertThat(openSearchShards).isEqualTo("1");
  }

  private static String elasticsearchShards(
      final IndexMappingCreator<IndexSettings.Builder> index,
      final ConfigurationService configuration)
      throws IOException {
    return index
        .getStaticSettings(new IndexSettings.Builder(), configuration)
        .build()
        .numberOfShards();
  }

  private static String openSearchShards(
      final IndexMappingCreator<org.opensearch.client.opensearch.indices.IndexSettings.Builder>
          index,
      final ConfigurationService configuration)
      throws IOException {
    return String.valueOf(
        index
            .getStaticSettings(
                new org.opensearch.client.opensearch.indices.IndexSettings.Builder(), configuration)
            .build()
            .numberOfShards());
  }
}
