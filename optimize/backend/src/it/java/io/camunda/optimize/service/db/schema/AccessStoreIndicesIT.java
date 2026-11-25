/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.AUTHORIZATION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_RULE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ROLE_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import org.junit.jupiter.api.Test;

class AccessStoreIndicesIT extends AbstractBrokerlessZeebeCCSMIT {

  @Test
  void shouldCreateTheAccessStoreIndicesOnStartup() {
    // when Optimize has started

    // then
    assertThat(databaseIntegrationTestExtension.indexExists(ROLE_INDEX_NAME)).isTrue();
    assertThat(databaseIntegrationTestExtension.indexExists(MAPPING_RULE_INDEX_NAME)).isTrue();
    assertThat(databaseIntegrationTestExtension.indexExists(AUTHORIZATION_INDEX_NAME)).isTrue();
  }
}
