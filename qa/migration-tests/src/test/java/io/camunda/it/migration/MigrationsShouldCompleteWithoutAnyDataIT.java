/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.application.Profile;
import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.apache.commons.lang3.exception.UncheckedException;
import org.elasticsearch.core.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class MigrationsShouldCompleteWithoutAnyDataIT {

  @RegisterExtension
  private static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withPostUpdateAdditionalProfiles(
              Profile.PROCESS_MIGRATION, Profile.USAGE_METRIC_MIGRATION, Profile.TASK_MIGRATION);

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  void allMigrationsHaveRun(final CamundaMigrator migrator) {
    final var migrationIds = List.of("1", "2");
    final var tasklistMigrationIndex =
        new TasklistMigrationRepositoryIndex(migrator.getIndexPrefix(), migrator.isElasticsearch());
    final var operateMigrationIndex =
        new MigrationRepositoryIndex(migrator.getIndexPrefix(), migrator.isElasticsearch());

    migrationIds.forEach(
        id -> {
          try {
            assertMigrationStepsExist(tasklistMigrationIndex.getFullQualifiedName(), id);
            assertMigrationStepsExist(operateMigrationIndex.getFullQualifiedName(), id);
          } catch (final IOException | InterruptedException e) {
            throw new UncheckedException(e);
          }
        });
  }

  private void assertMigrationStepsExist(final String index, final String migrationIdSuffix)
      throws IOException, InterruptedException {
    final var url = PROVIDER.getDatabaseUrl();
    final var migrationId = VersionUtil.getVersion() + "-" + migrationIdSuffix;
    final var targetUrl = String.format("%s/%s/_doc/%s", url, index, migrationId);

    final HttpRequest request =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(targetUrl))
            .header("Content-Type", "application/json")
            .build();

    final HttpResponse<Void> response = HTTP_CLIENT.send(request, BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }
}
