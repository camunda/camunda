/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.processdefinition;

import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveProcessDefinition;
import static io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures.createAndSaveRandomProcessDefinitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.service.ProcessDefinitionReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionSort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ProcessDefinitionIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindProcessDefinitionByKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition = ProcessDefinitionFixtures.createRandomized(b -> b);
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var instance =
        processDefinitionReader.findOne(processDefinition.processDefinitionKey()).orElse(null);
    assertThat(instance).isNotNull();
    assertThat(instance.key()).isEqualTo(processDefinition.processDefinitionKey());
    assertThat(instance.bpmnProcessId()).isEqualTo(processDefinition.processDefinitionId());
    assertThat(instance.version()).isEqualTo(processDefinition.version());
    assertThat(instance.name()).isEqualTo(processDefinition.name());
    assertThat(instance.resourceName()).isEqualTo(processDefinition.resourceName());
  }

  @TestTemplate
  public void shouldFindProcessInstanceByBpmnProcessId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition =
        ProcessDefinitionFixtures.createRandomized(
            b -> b.processDefinitionId("test-process-unique"));
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds("test-process-unique")
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);

    final var instance = searchResult.hits().getFirst();

    assertThat(instance.key()).isEqualTo(processDefinition.processDefinitionKey());
    assertThat(instance.bpmnProcessId()).isEqualTo(processDefinition.processDefinitionId());
    assertThat(instance.version()).isEqualTo(processDefinition.version());
    assertThat(instance.name()).isEqualTo(processDefinition.name());
    assertThat(instance.resourceName()).isEqualTo(processDefinition.resourceName());
  }

  @TestTemplate
  public void shouldFindAllProcessDefinitionPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final String processDefinitionId = ProcessDefinitionFixtures.nextStringId();
    createAndSaveRandomProcessDefinitions(
        rdbmsWriter, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionIds(processDefinitionId)
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.hits()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllProcessInstancePageValuesAreNull(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    createAndSaveRandomProcessDefinitions(rdbmsWriter);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder().build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(null).size(null))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isGreaterThanOrEqualTo(20);
    assertThat(searchResult.hits()).hasSizeGreaterThanOrEqualTo(20);
  }

  @TestTemplate
  public void shouldFindProcessInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final ProcessDefinitionReader processDefinitionReader =
        rdbmsService.getProcessDefinitionReader();

    final var processDefinition = ProcessDefinitionFixtures.createRandomized(b -> b);
    createAndSaveRandomProcessDefinitions(rdbmsWriter);
    createAndSaveProcessDefinition(rdbmsWriter, processDefinition);

    final var searchResult =
        processDefinitionReader.search(
            new ProcessDefinitionDbQuery(
                new ProcessDefinitionFilter.Builder()
                    .processDefinitionKeys(processDefinition.processDefinitionKey())
                    .processDefinitionIds(processDefinition.processDefinitionId())
                    .names(processDefinition.name())
                    .resourceNames(processDefinition.resourceName())
                    .versions(processDefinition.version())
                    .versionTags(processDefinition.versionTag())
                    .tenantIds(processDefinition.tenantId())
                    .build(),
                ProcessDefinitionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.hits()).hasSize(1);
    assertThat(searchResult.hits().getFirst().key())
        .isEqualTo(processDefinition.processDefinitionKey());
  }
}