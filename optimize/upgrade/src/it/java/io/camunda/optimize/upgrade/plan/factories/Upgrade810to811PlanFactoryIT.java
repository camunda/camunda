/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.db.es.schema.index.AgentDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.AgentRunIndexES;
import io.camunda.optimize.service.db.os.schema.index.AgentDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.AgentRunIndexOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link Upgrade810to811PlanFactory} creates the agent run and agent definition
 * indices on an 8.10 cluster, so upgraded and fresh-install schemas stay in parity.
 */
public class Upgrade810to811PlanFactoryIT extends AbstractUpgradeIT {

  private static final String FROM_PATCH = "8.10.0";

  @Test
  public void shouldCreateAgentIndicesOnUpgrade() {
    // given an 8.10 cluster without the agent indices
    setMetadataVersion(FROM_PATCH);
    assertThat(getIndicesForMapping(agentRunIndex())).isEmpty();
    assertThat(getIndicesForMapping(agentDefinitionIndex())).isEmpty();

    // when
    performUpgrade();

    // then
    assertThat(getIndicesForMapping(agentRunIndex())).isNotEmpty();
    assertThat(getIndicesForMapping(agentDefinitionIndex())).isNotEmpty();
  }

  @Test
  public void shouldCreateAgentIndicesWithTheirShardCountsOnUpgrade() throws IOException {
    // given
    setMetadataVersion(FROM_PATCH);

    // when
    performUpgrade();

    // then the agent run index gets its own default and the definition index keeps one shard
    assertThat(databaseIntegrationTestExtension.getNumberOfShards(AGENT_INSTANCE_INDEX_NAME))
        .isEqualTo(3);
    assertThat(databaseIntegrationTestExtension.getNumberOfShards(AGENT_DEFINITION_INDEX_NAME))
        .isEqualTo(1);
  }

  private void performUpgrade() {
    final UpgradePlan plan =
        new Upgrade810to811PlanFactory().createUpgradePlan(upgradeDependencies);
    upgradeProcedure.performUpgrade(plan);
  }

  private IndexMappingCreator agentRunIndex() {
    return isElasticSearchUpgrade() ? new AgentRunIndexES() : new AgentRunIndexOS();
  }

  private IndexMappingCreator agentDefinitionIndex() {
    return isElasticSearchUpgrade() ? new AgentDefinitionIndexES() : new AgentDefinitionIndexOS();
  }
}
