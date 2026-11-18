/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.ProcessDefinitionState;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper.ProcessDefinitionStateDto;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * Proves that a suspended process definition version is returned as {@code SUSPENDED} through the
 * REST search API and that the latest version is taken after the state filter. The engine has no
 * processor for process definition suspension yet (#65684), so the suspend endpoint cannot produce
 * the state and the test writes it straight into secondary storage. Switch to the endpoint once
 * #65684 lands.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "(?i)aws_os")
class ProcessDefinitionSuspendedStateSearchIT {

  @MultiDbTestApplication static final TestStandaloneBroker BROKER = new TestStandaloneBroker();

  private static CamundaClient camundaClient;
  private static DatabaseType databaseType;

  @Test
  void shouldSearchBySuspendedState() throws IOException {
    // given
    final var versions = deployTwoVersions();
    writeState(versions.v2(), false);
    awaitState(versions.v2(), ProcessDefinitionState.SUSPENDED);

    // when
    final var suspended =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(versions.v2().getBpmnProcessId())
                        .state(ProcessDefinitionState.SUSPENDED))
            .send()
            .join()
            .items();

    // then
    assertThat(suspended)
        .extracting(ProcessDefinition::getProcessDefinitionKey)
        .containsExactly(versions.v2().getProcessDefinitionKey());
  }

  @Test
  void shouldReturnLatestActiveVersionSkippingSuspendedVersion() throws IOException {
    // given
    final var versions = deployTwoVersions();
    writeState(versions.v2(), false);
    awaitState(versions.v2(), ProcessDefinitionState.SUSPENDED);

    // when
    final var latestActive =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(versions.v2().getBpmnProcessId())
                        .isLatestVersion(true)
                        .state(ProcessDefinitionState.ACTIVE))
            .send()
            .join()
            .items();

    // then
    assertThat(latestActive)
        .extracting(ProcessDefinition::getProcessDefinitionKey)
        .containsExactly(versions.v1().getProcessDefinitionKey());
  }

  private static Versions deployTwoVersions() {
    final var processId = Strings.newRandomValidBpmnId();
    final var v1 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent("end1").done(),
            processId + ".bpmn");
    final var v2 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent("end2").done(),
            processId + ".bpmn");
    assertThat(v2.getVersion()).isGreaterThan(v1.getVersion());
    return new Versions(v1, v2);
  }

  private static void awaitState(final Process process, final ProcessDefinitionState expected) {
    Awaitility.await("process definition should be " + expected)
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newProcessDefinitionGetRequest(process.getProcessDefinitionKey())
                            .send()
                            .join()
                            .getState())
                    .isEqualTo(expected));
  }

  /** Writes the state of the process definition straight into the secondary storage. */
  private static void writeState(final Process process, final boolean active) throws IOException {
    final var key = process.getProcessDefinitionKey();
    final var physicalTenantId =
        Optional.ofNullable(CamundaMultiDBExtension.getPhysicalTenant())
            .orElse(DEFAULT_PHYSICAL_TENANT_ID);
    switch (databaseType.storageType()) {
      case ELASTICSEARCH ->
          BROKER
              .bean(ElasticsearchClient.class)
              .update(
                  u ->
                      u.index(processIndexName(physicalTenantId))
                          .id(String.valueOf(key))
                          .doc(Map.of(ProcessIndex.STATE, storageState(active)))
                          .refresh(co.elastic.clients.elasticsearch._types.Refresh.True),
                  Object.class);
      case OPENSEARCH ->
          BROKER
              .bean(OpenSearchClient.class)
              .update(
                  u ->
                      u.index(processIndexName(physicalTenantId))
                          .id(String.valueOf(key))
                          .doc(Map.of(ProcessIndex.STATE, storageState(active)))
                          .refresh(org.opensearch.client.opensearch._types.Refresh.True),
                  Object.class);
      case RDBMS -> {
        final var mapper =
            BROKER
                .<Map<String, RdbmsMapperBundle>>bean("rdbmsMapperBundles")
                .get(physicalTenantId)
                .processDefinitionMapper();
        mapper.updateState(
            new ProcessDefinitionStateDto(
                key,
                active
                    ? io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState
                        .ACTIVE
                    : io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState
                        .SUSPENDED));
      }
      default -> throw new IllegalStateException("Unsupported database " + databaseType);
    }
  }

  private static String storageState(final boolean active) {
    return (active
            ? io.camunda.webapps.schema.entities.ProcessDefinitionState.ACTIVE
            : io.camunda.webapps.schema.entities.ProcessDefinitionState.SUSPENDED)
        .name();
  }

  private static String processIndexName(final String physicalTenantId) {
    return BROKER
        .<Map<String, IndexDescriptors>>bean("physicalTenantScopedIndexDescriptors")
        .get(physicalTenantId)
        .get(ProcessIndex.class)
        .getFullQualifiedName();
  }

  private record Versions(Process v1, Process v2) {}
}
