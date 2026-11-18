/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.security.clusteradmin.ClusterAdminUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConfigurationValuesTest {

  @Test
  void shouldReplaceTheValuesOfNestedProperties() {
    // given
    final var camunda = new Camunda();
    camunda.getData().getSecondaryStorage().getElasticsearch().setPassword("secret:es-password");
    camunda.getData().getPrimaryStorage().getBackup().getS3().setSecretKey("secret:s3-key");
    camunda
        .getSecurity()
        .getClusterAdmin()
        .getBasic()
        .setUsers(List.of(new ClusterAdminUser("admin", "secret:admin-password")));

    // when
    final Map<String, String> visited = new HashMap<>();
    ConfigurationValues.walk(
        camunda,
        (property, field, value) -> {
          if (!value.startsWith("secret:")) {
            return value;
          }
          visited.put(property, value);
          return "resolved-" + value.substring("secret:".length());
        });

    // then
    assertThat(visited)
        .containsOnly(
            Map.entry("data.secondary-storage.elasticsearch.password", "secret:es-password"),
            Map.entry("data.primary-storage.backup.s3.secret-key", "secret:s3-key"),
            Map.entry("security.cluster-admin.basic.users[0].password", "secret:admin-password"));
    assertThat(camunda.getData().getSecondaryStorage().getElasticsearch().getPassword())
        .isEqualTo("resolved-es-password");
    assertThat(camunda.getData().getPrimaryStorage().getBackup().getS3().getSecretKey())
        .isEqualTo("resolved-s3-key");
    assertThat(camunda.getSecurity().getClusterAdmin().getBasic().getUsers())
        .containsExactly(new ClusterAdminUser("admin", "resolved-admin-password"));
  }

  @Test
  void shouldNameTheEntryOfAMap() {
    // given
    final var camunda = new Camunda();
    final var store = new Document.AzureStore();
    store.setConnectionString("secret:azure");
    camunda.getDocument().getAzure().put("main", store);

    // when
    final Map<String, String> visited = new HashMap<>();
    ConfigurationValues.walk(
        camunda,
        (property, field, value) -> {
          visited.put(property, value);
          return value;
        });

    // then
    assertThat(visited).containsEntry("document.azure.main.connection-string", "secret:azure");
  }
}
