/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.awaitility.Awaitility;

public class MigrationTestUtils {

  public static void awaitProcessDefinitionCreated(
      final long processDefinitionKey, final CamundaMigrator migrator) {
    final ObjectMapper mapper = new ObjectMapper();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () -> {
              final var res =
                  migrator.request(
                      b ->
                          b.GET()
                              .uri(
                                  URI.create(
                                      migrator.getWebappsUrl()
                                          + "/process-definitions/"
                                          + processDefinitionKey)),
                      HttpResponse.BodyHandlers.ofString());

              if (res.statusCode() == 200) {
                final var proc = mapper.readValue(res.body(), ProcessDefinition.class);
                return proc.getKey() == processDefinitionKey;
              }
              return false;
            });
  }

  public static Optional<ProcessEntity> findProcess(
      final CamundaMigrator migrator, final long processDefinitionKey) {
    final var processDefinitionQuery =
        SearchQueryBuilders.term(ProcessIndex.KEY, processDefinitionKey);
    final var req =
        SearchQueryRequest.of(
            s ->
                s.query(processDefinitionQuery)
                    .index(migrator.indexFor(ProcessIndex.class).getAlias()));

    return migrator.getSearchClient().search(req, ProcessEntity.class).hits().stream()
        .findFirst()
        .map(SearchQueryHit::source);
  }

  public static void deployForm(final CamundaMigrator migrator, final String resource) {
    migrator
        .getCamundaClient()
        .newDeployResourceCommand()
        .addResourceFromClasspath("form/form.form")
        .send()
        .join();
  }

  public static long deployProcessDefinition(
      final CamundaMigrator migrator, final String resource) {
    final var processDefinitionKey =
        migrator
            .getCamundaClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath(resource)
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    return processDefinitionKey;
  }
}
