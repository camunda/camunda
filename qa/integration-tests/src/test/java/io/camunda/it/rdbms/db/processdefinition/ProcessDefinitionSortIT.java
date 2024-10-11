/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionSort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionSortIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String name = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .name(name)
                .processDefinitionKey(processDefinitionKey1)
                .processDefinitionId("test-process-2")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .name(name)
                .processDefinitionKey(processDefinitionKey2)
                .processDefinitionId("test-process-1")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .name(name)
                .processDefinitionKey(processDefinitionKey3)
                .processDefinitionId("test-process-4")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .name(name)
                .processDefinitionKey(processDefinitionKey4)
                .processDefinitionId("test-process-3")
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .names(name)
                    .build(),
                ProcessDefinitionSort.of(b -> b.processDefinitionId().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey2, processDefinitionKey1, processDefinitionKey4,
            processDefinitionKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByProcessDefinitionKey(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String name = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey2)
                .name(name)
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey1)
                .name(name)
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey4)
                .name(name)
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey3)
                .name(name)
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .names(name)
                    .build(),
                ProcessDefinitionSort.of(b -> b.processDefinitionKey().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey1, processDefinitionKey2, processDefinitionKey3,
            processDefinitionKey4);
  }

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByName(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey1)
                .processDefinitionId(processDefinitionId)
                .name("Process 2")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey2)
                .processDefinitionId(processDefinitionId)
                .name("Process 1")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey3)
                .processDefinitionId(processDefinitionId)
                .name("Process 4")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey4)
                .processDefinitionId(processDefinitionId)
                .name("Process 3")
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b.name().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey2, processDefinitionKey1, processDefinitionKey4,
            processDefinitionKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByResourceName(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey1)
                .processDefinitionId(processDefinitionId)
                .resourceName("process2.bpmn")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey2)
                .processDefinitionId(processDefinitionId)
                .resourceName("process1.bpmn")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey3)
                .processDefinitionId(processDefinitionId)
                .resourceName("process4.bpmn")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey4)
                .processDefinitionId(processDefinitionId)
                .resourceName("process3.bpmn")
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b.resourceName().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey2, processDefinitionKey1, processDefinitionKey4,
            processDefinitionKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByVersion(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey1)
                .processDefinitionId(processDefinitionId)
                .version(2)
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey2)
                .processDefinitionId(processDefinitionId)
                .version(1)
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey3)
                .processDefinitionId(processDefinitionId)
                .version(4)
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey4)
                .processDefinitionId(processDefinitionId)
                .version(3)
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b.version().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey2, processDefinitionKey1, processDefinitionKey4,
            processDefinitionKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByVersionTag(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey1)
                .processDefinitionId(processDefinitionId)
                .versionTag("Version 2")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey2)
                .processDefinitionId(processDefinitionId)
                .versionTag("Version 1")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey3)
                .processDefinitionId(processDefinitionId)
                .versionTag("Version 4")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey4)
                .processDefinitionId(processDefinitionId)
                .versionTag("Version 3")
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b.versionTag().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey2, processDefinitionKey1, processDefinitionKey4,
            processDefinitionKey3);
  }

  @TestTemplate
  public void shouldSortAllProcessDefinitionsByTenant(
      final CamundaRdbmsTestApplication testApplication) {
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var reader = rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = UUID.randomUUID().toString(); // For Test Scope
    final Long processDefinitionKey1 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey2 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey3 = ProcessDefinitionFixtures.nextKey();
    final Long processDefinitionKey4 = ProcessDefinitionFixtures.nextKey();
    createAndSaveProcessDefinitions(
        rdbmsWriter,
        List.of(
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey1)
                .processDefinitionId(processDefinitionId)
                .tenantId("Tenant 2")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey2)
                .processDefinitionId(processDefinitionId)
                .tenantId("Tenant 1")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey3)
                .processDefinitionId(processDefinitionId)
                .tenantId("Tenant 4")
            ),
            ProcessDefinitionFixtures.createRandomized(b -> b
                .processDefinitionKey(processDefinitionKey4)
                .processDefinitionId(processDefinitionId)
                .tenantId("Tenant 3")
            )
        ));

    final var searchResult =
        reader.search(new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b.tenantId().asc()),
                SearchQueryPage.of(b -> b)))
            .hits();

    assertThat(searchResult).hasSize(4);
    assertThat(searchResult.stream().map(ProcessDefinitionEntity::key).toList())
        .containsExactly(
            processDefinitionKey2, processDefinitionKey1, processDefinitionKey4,
            processDefinitionKey3);
  }
}
