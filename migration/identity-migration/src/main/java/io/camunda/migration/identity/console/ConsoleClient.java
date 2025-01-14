/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.console;

import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.UserRoles;
import java.util.List;
import org.springframework.web.client.RestTemplate;

public class ConsoleClient {

  private static final String MIGRATION_DATA_ENDPOINT =
      "/external/organizations/%s/clusters/%s/migrationData/%s";

  private final String organizationId;
  private final String clusterId;
  private final String internalClientId;
  private final RestTemplate restTemplate;

  public ConsoleClient(
      final RestTemplate restTemplate,
      final String organizationId,
      final String clusterId,
      final String internalClientId) {
    this.organizationId = organizationId;
    this.clusterId = clusterId;
    this.internalClientId = internalClientId;
    this.restTemplate = restTemplate;
  }

  public List<Client> fetchClients() {
    return fetchMigrationData().clients();
  }

  private MigrationData fetchMigrationData() {
    return restTemplate.getForObject(
        String.format(MIGRATION_DATA_ENDPOINT, organizationId, clusterId, internalClientId),
        MigrationData.class);
  }

  record MigrationData(List<Client> clients, List<UserRoles> members) {}
}
