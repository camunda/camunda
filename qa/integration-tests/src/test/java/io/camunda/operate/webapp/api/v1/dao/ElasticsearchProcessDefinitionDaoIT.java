/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.indices.ProcessIndex.NAME;
import static io.camunda.operate.webapp.api.v1.entities.ProcessDefinition.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Test;
import org.mockito.internal.matchers.Or;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ElasticsearchProcessDefinitionDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  ElasticsearchProcessDefinitionDao dao;

  private Results<ProcessDefinition> processDefinitionResults;
  private ProcessDefinition processDefinition;
  private Long key;
  private String processDefinitionAsXML;
  private List<Long> processDefinitionKeys;
  private ChangeStatus changeStatus;

  @Test
  public void shouldReturnEmptyListWhenNoProcessDefinitionsExist() throws Exception {
    given(() -> { /*"no process definitions"*/ });
    when(() -> processDefinitionResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(processDefinitionResults.getItems()).isEmpty();
      assertThat(processDefinitionResults.getTotal()).isZero();
    });

    given(() -> deployProcesses(
        "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn"));
    when(() -> processDefinitionResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
      assertThat(processDefinitionResults.getItems()).extracting(BPMN_PROCESS_ID)
          .containsExactly("demoProcess", "errorProcess", "complexProcess");
    });
  }

  @Test
  public void shouldReturnWhenByKey() throws Exception {
    given(() -> {
      deployProcesses("complexProcess_v_3.bpmn");
      processDefinitionResults = dao.search(new Query<>());
      key = processDefinitionResults.getItems().get(0).getKey();
    });
    when(() -> processDefinition = dao.byKey(key));
    then(() -> {
      assertThat(processDefinition.getKey()).isEqualTo(key);
      assertThat(processDefinition.getBpmnProcessId()).isEqualTo("complexProcess");
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void showThrowWhenByKeyNotExists() throws Exception {
    given(() -> {
    });
    when(() -> dao.byKey(-27L));
  }

  @Test(expected = ServerException.class)
  public void shouldThrowWhenByKeyFails() throws Exception {
    given(() -> {
    });
    when(() -> dao.byKey(null));
  }

  @Test
  public void shouldReturnWhenXmlByKey() throws Exception {
    given(() -> {
      deployProcesses("complexProcess_v_3.bpmn");
      processDefinitionResults = dao.search(new Query<>());
      key = processDefinitionResults.getItems().get(0).getKey();
    });
    when(() -> processDefinitionAsXML = dao.xmlByKey(key));
    then(() -> {
      assertThat(processDefinitionAsXML).contains("complexProcess");
      assertThatIsXML(processDefinitionAsXML);
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void showThrowWhenXmlByKeyNotExists() throws Exception {
    given(() -> {
    });
    when(() -> dao.byKey(-27L));
  }

  @Test(expected = ServerException.class)
  public void shouldThrowWhenXmlByKeyFails() throws Exception {
    given(() -> {
    });
    when(() -> dao.byKey(null));
  }

  @Test
  public void shouldPagedWithSearchAfterSizeAndSorted() throws Exception {
    given(() -> processDefinitionKeys = deployProcesses(
        "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn",
        "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn"));

    when(() ->
        processDefinitionResults = dao.search(new Query<ProcessDefinition>()
            .setSize(3).setSearchAfter(new Object[]{"errorProcess", processDefinitionKeys.get(2).toString()})
            .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.DESC)))
    );
    then(() -> {
      assertThat(processDefinitionResults.getTotal()).isEqualTo(6);
      List<ProcessDefinition> processDefinitions = processDefinitionResults.getItems();
      assertThat(processDefinitions).hasSize(3);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .containsExactly("error-end-process","demoProcess","complexProcess");
    });
  }

  @Test
  public void shouldPagedWithSearchAfterSizeAndSortedAsc() throws Exception {
    given(() -> processDefinitionKeys = deployProcesses(
        "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn",
        "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn"));

    when(() ->
        processDefinitionResults = dao.search(new Query<ProcessDefinition>()
            .setSize(3).setSearchAfter(new Object[]{"errorProcess", processDefinitionKeys.get(3)})
            .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.ASC)))
    );
    then(() -> {
      assertThat(processDefinitionResults.getTotal()).isEqualTo(6);
      List<ProcessDefinition> processDefinitions = processDefinitionResults.getItems();
      assertThat(processDefinitions).hasSize(2);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .containsExactly("intermediate-throw-event-process","message-end-event-process");
    });
  }

  @Test
  public void shouldFilteredByFieldsAndSortedDesc() throws Exception {
    given(() -> deployProcesses(
        "demoProcess_v_1.bpmn", "demoProcess_v_2.bpmn", "complexProcess_v_3.bpmn",
        "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn"));

    when(() -> {
      final ProcessDefinition processDefinitionExample = new ProcessDefinition()
          .setName("Demo process");
      processDefinitionResults = dao.search(new Query<ProcessDefinition>()
          .setFilter(processDefinitionExample)
          .setSort(Sort.listOf(VERSION, Order.DESC)));
    });
    then(() -> {
      assertThat(processDefinitionResults.getTotal()).isEqualTo(2);
      List<ProcessDefinition> processDefinitions = processDefinitionResults.getItems();
      assertThat(processDefinitions).hasSize(2);
      assertThat(processDefinitions).extracting(NAME)
          .containsExactly("Demo process","Demo process");
      assertThat(processDefinitions).extracting(VERSION)
          .containsExactly(2,1);
    });
  }

  @Test
  public void shouldFilteredAndPagedAndSorted() throws Exception {
    given(() -> deployProcesses(
        "demoProcess_v_1.bpmn", "demoProcess_v_2.bpmn", "complexProcess_v_3.bpmn",
        "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn"));

    when(() -> {
      final ProcessDefinition processDefinitionExample = new ProcessDefinition()
          .setVersion(1);
      processDefinitionResults = dao.search(new Query<ProcessDefinition>()
          .setFilter(processDefinitionExample)
          .setSize(2)
          .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.DESC)));
    });
    then(() -> {
      assertThat(processDefinitionResults.getTotal()).isEqualTo(5);
      List<ProcessDefinition> processDefinitions = processDefinitionResults.getItems();
      assertThat(processDefinitions).hasSize(2);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .containsExactly("message-end-event-process","intermediate-throw-event-process");
      assertThat(processDefinitions).extracting(VERSION)
          .containsExactly(1,1);
    });
  }

  @Test
  public void shouldDeleteByKey() throws Exception {
    given(() -> deployProcesses(
        "demoProcess_v_1.bpmn", "demoProcess_v_2.bpmn", "complexProcess_v_3.bpmn",
        "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn"));
    when(() -> {
      processDefinitionResults = dao.search(new Query<>());
      key = processDefinitionResults.getItems().get(0).getKey();
      changeStatus = dao.delete(key);
    });
    then(() -> {
      assertThat(changeStatus.getDeleted()).isEqualTo(1);
      assertThat(changeStatus.getMessage()).contains(""+key);
      elasticsearchTestRule.refreshIndexesInElasticsearch();
      processDefinitionResults = dao.search(new Query<>());
      assertThat(processDefinitionResults.getItems().stream()
          .noneMatch(pd -> pd.getKey().equals(key))).isTrue();
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void shouldThrowForDeleteWhenKeyNotExists() throws Exception {
    given(() -> deployProcesses(
        "demoProcess_v_1.bpmn", "demoProcess_v_2.bpmn", "complexProcess_v_3.bpmn"));
    when(() -> dao.delete(123L));
  }

  protected void assertThatIsXML(String xml) {
    try {
      final InputStream xmlInputStream = new ByteArrayInputStream(
          xml.getBytes(StandardCharsets.UTF_8));
      new XMLUtil().getSAXParserFactory().newSAXParser()
          .parse(xmlInputStream, new DefaultHandler());
    } catch (SAXException | IOException | ParserConfigurationException e) {
      fail(String.format("String '%s' should be of type xml", xml), e);
    }
  }

  protected void given(Runnable conditions) throws Exception {
    conditions.run();
  }

  protected void when(Runnable actions) throws Exception {
    actions.run();
  }

  protected void then(Runnable asserts) throws Exception {
    asserts.run();
  }

}
