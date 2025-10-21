/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.it.migration.util.CamundaMigrator;
import io.camunda.it.migration.util.MigrationITExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class PostImporterNullTreePathIT {
  static List<Long> processInstanceKeys = new ArrayList<>();
  private static final String TREE_PATH_REGEX =
      "^PI_\\d{16}/FN_call-activity/FNI_\\d{16}/PI_\\d{16}/FN_user-task/FNI_\\d{16}$";

  @RegisterExtension
  private static final MigrationITExtension PROVIDER =
      new MigrationITExtension()
          .withInitialEnvOverrides(Map.of("CAMUNDA_OPERATE_IMPORTER_POSTIMPORTENABLED", "false"))
          .withBeforeUpgradeConsumer(PostImporterNullTreePathIT::setup);

  @AfterAll
  static void cleanUpAfterAll() {
    processInstanceKeys.clear();
  }

  private static void setup(final DatabaseType databaseType, final CamundaMigrator migrator) {
    migrator
        .getCamundaClient()
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("child")
                .startEvent()
                .subProcess("sub-process")
                .embeddedSubProcess()
                .startEvent()
                .userTask("user-task")
                // add a form that does not exist to create an incident
                .zeebeFormId("missing-form")
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done(),
            "nested.bpmn")
        .addProcessModel(
            Bpmn.createExecutableProcess("parent")
                .startEvent()
                .callActivity("call-activity")
                .zeebeProcessId("child")
                .done(),
            "parent.bpmn")
        .send()
        .join();

    for (int i = 0; i < 10; i++) {
      final var piKey =
          migrator
              .getCamundaClient()
              .newCreateInstanceCommand()
              .bpmnProcessId("parent")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      processInstanceKeys.add(piKey);
    }
  }

  @Test
  void shouldHaveIncidentProcessed(final CamundaMigrator migrator) {

    // verify incidents are handled and propagated to the parent process instance
    Awaitility.await()
        .untilAsserted(
            () ->
                processInstanceKeys.forEach(
                    piKey -> {
                      final var incident =
                          migrator
                              .getCamundaClient()
                              .newIncidentSearchRequest()
                              .filter(f -> f.processInstanceKey(piKey))
                              .send()
                              .join();
                      assertThat(incident).isNotNull();
                    }));

    final var hits =
        migrator
            .getSearchClient()
            .search(
                req -> req.index(migrator.indexFor(IncidentTemplate.class).getAlias()),
                IncidentEntity.class)
            .hits();

    assertThat(hits.size()).isEqualTo(processInstanceKeys.size());

    hits.stream()
        .map(SearchQueryHit::source)
        .forEach(
            incident -> {
              assertThat(incident.getState()).isEqualTo(IncidentState.ACTIVE);
              assertThat(incident.getTreePath()).isNotBlank();
              // we cannot reliably assert the treePath, see
              // https://github.com/camunda/camunda/issues/39653
              // assertThat(incident.getTreePath()).matches(TREE_PATH_REGEX);
              // final String piKey = incident.getTreePath().split("/")[0].substring(3);
              // assertThat(processInstanceKeys.contains(Long.parseLong(piKey))).isTrue();
            });
  }
}
