/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import com.github.dockerjava.api.command.CreateContainerCmd;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class StartStandalonePrefixMigrationCamundaDockerIT extends AbstractCamundaDockerIT {
  // Regression for https://github.com/camunda/camunda/issues/35520
  @Test
  public void testStartStandalonePrefixMigration() throws Exception {
    // given
    // create and start Elasticsearch container
    final ElasticsearchContainer elasticsearchContainer =
        createContainer(this::createElasticsearchContainer);
    elasticsearchContainer.start();

    // create camunda container with only StandalonePrefixMigration app
    final var prefixMigrationContainer =
        new GenericContainer<>(CAMUNDA_TEST_DOCKER_IMAGE)
            .withCreateContainerCmdModifier(
                (final CreateContainerCmd cmd) ->
                    cmd.withEntrypoint("/usr/local/camunda/bin/prefix-migration"))
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(180)))
            .withNetwork(network)
            .withNetworkAliases(CAMUNDA_NETWORK_ALIAS)
            // Unified Configuration
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DATABASE_TYPE)
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", elasticsearchUrl())
            // ---
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", "some-prefix");

    // when - then the container should start without errors
    startContainer(createContainer(() -> prefixMigrationContainer));
  }
}
