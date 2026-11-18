/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Optimize creates the agent indices on startup with their own shard counts. Runs
 * against whichever backend the IT suite is configured with (Elasticsearch or OpenSearch).
 */
class AgentIndicesIT extends AbstractBrokerlessZeebeCCSMIT {

  @Test
  void shouldCreateTheAgentIndicesOnStartup() {
    // when Optimize has started

    // then
    assertThat(databaseIntegrationTestExtension.indexExists(AGENT_INSTANCE_INDEX_NAME)).isTrue();
    assertThat(databaseIntegrationTestExtension.indexExists(AGENT_DEFINITION_INDEX_NAME)).isTrue();
  }

  @Test
  void shouldCreateTheAgentRunIndexWithItsOwnShardCount() throws IOException {
    // when Optimize has started with the shipped configuration

    // then the agent run index uses its own default and the definition index keeps one shard
    assertThat(databaseIntegrationTestExtension.getNumberOfShards(AGENT_INSTANCE_INDEX_NAME))
        .isEqualTo(3);
    assertThat(databaseIntegrationTestExtension.getNumberOfShards(AGENT_DEFINITION_INDEX_NAME))
        .isEqualTo(1);
  }
}
