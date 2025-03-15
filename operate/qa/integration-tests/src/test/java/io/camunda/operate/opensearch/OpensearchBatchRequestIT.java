/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.DatabaseCondition;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OpensearchOperateAbstractIT;
import io.camunda.operate.util.TestUtil;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = DatabaseCondition.DATABASE_PROPERTY + "=opensearch")
public class OpensearchBatchRequestIT extends OpensearchOperateAbstractIT {

  private static SchemaWithExporter schemaExporterHelper;
  @Autowired RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("operateProcessIndex")
  ProcessIndex processIndex;

  @BeforeClass
  public static void beforeClass() {
    schemaExporterHelper = new SchemaWithExporter("", false);
  }

  @Before
  public void setUp() throws IOException {
    schemaExporterHelper.createSchema();
  }

  @After
  public void cleanUp() {
    TestUtil.removeAllIndices(richOpenSearchClient.index(), richOpenSearchClient.template(), "*");
  }

  @Test
  public void canUseRichClient() {
    assertThat(richOpenSearchClient).isNotNull();
    assertThat(searchForProcessEntity(matchAll())).isEmpty();
  }

  @Test
  public void shouldAdd() throws PersistenceException {
    // given
    final var batchRequest = richOpenSearchClient.batch().newBatchRequest();
    batchRequest
        .add(
            processIndex.getFullQualifiedName(),
            new ProcessEntity()
                .setId("1")
                .setBpmnProcessId("bpmnProcessId")
                .setVersion(1)
                .setBpmnXml("xml")
                .setResourceName("resource")
                .setName("name1"))
        .add(
            processIndex.getFullQualifiedName(),
            new ProcessEntity()
                .setId("2")
                .setBpmnProcessId("bpmnProcessId2")
                .setVersion(1)
                .setBpmnXml("xml")
                .setResourceName("resource")
                .setName("name2"))
        .executeWithRefresh();
    // when
    final var foundProcesses = searchForProcessEntity(matchAll());
    // then
    assertThat(foundProcesses.size()).isEqualTo(2);
    final var firstFoundProcess = foundProcesses.get(0);
    assertThat(firstFoundProcess.getName()).isEqualTo("name1");
    assertThat(firstFoundProcess.getId()).isEqualTo("1");
  }

  @Test
  public void shouldUpdateWithIdAndOperateEntity() throws PersistenceException {
    // given
    shouldAdd();
    final var newProcessEntity =
        new ProcessEntity()
            .setId("1")
            .setBpmnProcessId("bpmnProcessId")
            .setVersion(1)
            .setBpmnXml("xml")
            .setResourceName("resource")
            .setName("newName");
    // when
    newBatchRequest()
        .update(processIndex.getFullQualifiedName(), "1", newProcessEntity)
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("newName");
  }

  @Test
  public void shouldUpdateWithIdAndFields() throws PersistenceException {
    // given
    shouldAdd();
    // when
    newBatchRequest()
        .update(processIndex.getFullQualifiedName(), "1", Map.of("name", "newName"))
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("newName");
  }

  @Test
  public void shouldUpdateWithScript() throws PersistenceException {
    // given
    shouldAdd();
    // when
    final var script = "ctx._source.name += params.secondName;";
    final Map<String, Object> parameters = Map.of("secondName", "-anotherName");
    newBatchRequest()
        .updateWithScript(processIndex.getFullQualifiedName(), "1", script, parameters)
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("name1-anotherName");
  }

  @Test
  public void shouldUpsert() throws PersistenceException {
    // given
    shouldAdd();
    // when
    final var processEntity =
        new ProcessEntity()
            .setId("5")
            .setBpmnProcessId("bpmnProcessId")
            .setVersion(1)
            .setBpmnXml("xml")
            .setResourceName("resource")
            .setName("name5");
    newBatchRequest()
        .upsert(processIndex.getFullQualifiedName(), "5", processEntity, Map.of())
        .executeWithRefresh();
    // then
    final var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 5L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("name5");
  }

  private BatchRequest newBatchRequest() {
    return richOpenSearchClient.batch().newBatchRequest();
  }

  private List<ProcessEntity> searchForProcessEntity(final Query query) {
    return richOpenSearchClient
        .doc()
        .search(
            searchRequestBuilder(processIndex.getFullQualifiedName()).query(query),
            ProcessEntity.class)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .toList();
  }
}
