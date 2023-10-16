/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OpensearchOperateAbstractIT;
import io.camunda.operate.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

public class OpensearchBatchRequestIT extends OpensearchOperateAbstractIT {

  @Autowired
  RichOpenSearchClient richOpenSearchClient;

  @Autowired
  ProcessIndex processIndex;

  @Autowired
  SchemaManager schemaManager;

  @Autowired
  OperateProperties operateProperties;
  private String indexPrefix;

  @Before
  public void setUp(){
    indexPrefix = "test-batch-request-"+ TestUtil.createRandomString(5);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
    schemaManager.createSchema();
  }

  @After
  public void cleanUp() {
    schemaManager.deleteIndicesFor(indexPrefix +"*");
  }

  @Test
  public void canUseRichClient(){
    assertThat(richOpenSearchClient).isNotNull();
    assertThat(searchForProcessEntity(matchAll())).isEmpty();
  }

  @Test
  public void shouldAdd() throws PersistenceException {
    // given
    var batchRequest = richOpenSearchClient.batch().newBatchRequest();
    batchRequest.add(processIndex.getFullQualifiedName(),
        new ProcessEntity().setId("1").setBpmnProcessId("bpmnProcessId").setVersion(1)
            .setBpmnXml("xml").setResourceName("resource")
            .setName("name1"))
        .add(processIndex.getFullQualifiedName(),
            new ProcessEntity().setId("2").setBpmnProcessId("bpmnProcessId2").setVersion(1)
                .setBpmnXml("xml").setResourceName("resource")
                .setName("name2"))
        .executeWithRefresh();
    // when
    var foundProcesses = searchForProcessEntity(matchAll());
    // then
    assertThat(foundProcesses.size()).isEqualTo(2);
    var firstFoundProcess = foundProcesses.get(0);
    assertThat(firstFoundProcess.getName()).isEqualTo("name1");
    assertThat(firstFoundProcess.getId()).isEqualTo("1");
  }

  @Test
  public void shouldUpdateWithIdAndOperateEntity() throws PersistenceException {
    // given
    shouldAdd();
    var newProcessEntity = new ProcessEntity()
        .setId("1").setBpmnProcessId("bpmnProcessId").setVersion(1)
        .setBpmnXml("xml").setResourceName("resource")
        .setName("newName");
    // when
    newBatchRequest()
        .update(processIndex.getFullQualifiedName(),"1", newProcessEntity)
        .executeWithRefresh();
    // then
    var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("newName");
  }

  @Test
  public void shouldUpdateWithIdAndFields() throws PersistenceException {
    // given
    shouldAdd();
    // when
    newBatchRequest()
        .update(processIndex.getFullQualifiedName(),"1", Map.of("name","newName"))
        .executeWithRefresh();
    // then
    var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("newName");
  }

  @Test
  public void shouldUpdateWithScript() throws PersistenceException {
    // given
    shouldAdd();
    // when
    var script = "ctx._source.name += params.secondName;";
    Map<String,Object> parameters = Map.of("secondName","-anotherName");
    newBatchRequest()
        .updateWithScript(processIndex.getFullQualifiedName(), "1", script, parameters)
        .executeWithRefresh();
    // then
    var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 1L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("name1-anotherName");
  }

  @Test
  public void shouldUpsert() throws PersistenceException {
    // given
    shouldAdd();
    // when
    var processEntity = new ProcessEntity().setId("5").setBpmnProcessId("bpmnProcessId").setVersion(1)
        .setBpmnXml("xml").setResourceName("resource")
        .setName("name5");
    newBatchRequest()
        .upsert(processIndex.getFullQualifiedName(), "5", processEntity, Map.of())
        .executeWithRefresh();
    // then
    var foundProcesses = searchForProcessEntity(term(ProcessIndex.ID, 5L));
    assertThat(foundProcesses).size().isEqualTo(1);
    assertThat(foundProcesses.get(0).getName()).isEqualTo("name5");
  }

  private BatchRequest newBatchRequest(){
    return richOpenSearchClient.batch().newBatchRequest();
  }

  private List<ProcessEntity> searchForProcessEntity(Query query){
    return richOpenSearchClient.doc()
        .search(searchRequestBuilder(processIndex.getFullQualifiedName())
        .query(query), ProcessEntity.class)
        .hits().hits()
          .stream().map(Hit::source)
        .toList();
  }
}
