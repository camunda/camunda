/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ProcessDefinitionDaoIT extends OperateSearchAbstractIT {

  @Autowired private ProcessDefinitionDao dao;

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  private ProcessEntity pe1;
  private ProcessEntity pe2;
  private ProcessEntity pe3;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String resourceXml1 =
        testResourceManager.readResourceFileContentsAsString("demoProcess_v_1.bpmn");
    pe1 =
        new ProcessEntity()
            .setKey(2251799813685249L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Demo process")
            .setVersion(1)
            .setVersionTag("v1")
            .setBpmnProcessId("demoProcess")
            .setBpmnXml(resourceXml1);
    testSearchRepository.createOrUpdateDocumentFromObject(processIndex.getFullQualifiedName(), pe1);

    final String resourceXml2 =
        testResourceManager.readResourceFileContentsAsString("errorProcess.bpmn");
    pe2 =
        new ProcessEntity()
            .setKey(2251799813685251L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Error process")
            .setVersion(1)
            .setVersionTag("version1")
            .setBpmnProcessId("errorProcess")
            .setBpmnXml(resourceXml2);
    testSearchRepository.createOrUpdateDocumentFromObject(processIndex.getFullQualifiedName(), pe2);

    final String resourceXml3 =
        testResourceManager.readResourceFileContentsAsString("complexProcess_v_3.bpmn");
    pe3 =
        new ProcessEntity()
            .setKey(2251799813685253L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Complex process")
            .setVersion(1)
            .setBpmnProcessId("complexProcess")
            .setBpmnXml(resourceXml3);
    testSearchRepository.createOrUpdateDocumentFromObject(processIndex.getFullQualifiedName(), pe3);

    searchContainerManager.refreshIndices("*operate-process*");
  }

  @Test
  public void shouldReturnProcessDefinitions() {
    final Results<ProcessDefinition> processDefinitionResults = dao.search(new Query<>());

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder(
            pe1.getBpmnProcessId(), pe2.getBpmnProcessId(), pe3.getBpmnProcessId());
  }

  @Test
  public void shouldReturnWhenByKey() {
    final ProcessDefinition processDefinition = dao.byKey(pe1.getKey());

    assertThat(processDefinition.getBpmnProcessId()).isEqualTo(pe1.getBpmnProcessId());
    assertThat(processDefinition.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> dao.byKey(1L));
  }

  @Test
  public void shouldReturnWhenXmlByKey() {
    final String processDefinitionAsXml = dao.xmlByKey(pe1.getKey());

    assertThat(processDefinitionAsXml).contains(pe1.getBpmnProcessId());

    // Verify the returned string is xml
    try {
      final InputStream xmlInputStream =
          new ByteArrayInputStream(processDefinitionAsXml.getBytes(StandardCharsets.UTF_8));
      new XMLUtil()
          .getSAXParserFactory()
          .newSAXParser()
          .parse(xmlInputStream, new DefaultHandler());
    } catch (final SAXException | IOException | ParserConfigurationException e) {
      fail(String.format("String '%s' should be of type xml", processDefinitionAsXml), e);
    }
  }

  @Test
  public void showThrowWhenXmlByKeyNotExists() {
    assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> dao.xmlByKey(1L));
  }

  @Test
  public void shouldFilterProcessDefinitions() {
    final Results<ProcessDefinition> processDefinitionResults =
        dao.search(
            new Query<ProcessDefinition>()
                .setFilter(new ProcessDefinition().setBpmnProcessId(pe1.getBpmnProcessId())));

    assertThat(processDefinitionResults.getItems().get(0).getBpmnProcessId())
        .isEqualTo(pe1.getBpmnProcessId());
  }

  @Test
  public void shouldFilterProcessDefinitionsByVersionTag() {
    final Results<ProcessDefinition> processDefinitionResults =
        dao.search(
            new Query<ProcessDefinition>()
                .setFilter(new ProcessDefinition().setVersionTag(pe2.getVersionTag())));

    assertThat(processDefinitionResults.getItems()).hasSize(1);
    final var processDefinition = processDefinitionResults.getItems().getFirst();
    assertThat(processDefinition.getKey()).isEqualTo(pe2.getKey());
    assertThat(processDefinition.getVersionTag()).isEqualTo(pe2.getVersionTag());
  }

  @Test
  public void shouldSortProcessDefinitionsDesc() {
    final Results<ProcessDefinition> processDefinitionResults =
        dao.search(
            new Query<ProcessDefinition>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC)));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly(pe2.getBpmnProcessId(), pe1.getBpmnProcessId(), pe3.getBpmnProcessId());
  }

  @Test
  public void shouldSortProcessDefinitionsAsc() {
    final Results<ProcessDefinition> processDefinitionResults =
        dao.search(
            new Query<ProcessDefinition>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly(pe3.getBpmnProcessId(), pe1.getBpmnProcessId(), pe2.getBpmnProcessId());
  }

  @Test
  public void shouldPageProcessDefinitions() {
    Results<ProcessDefinition> processDefinitionResults =
        dao.search(
            new Query<ProcessDefinition>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC))
                .setSize(2));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).hasSize(2);

    assertThat(processDefinitionResults.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly(pe2.getBpmnProcessId(), pe1.getBpmnProcessId());

    final Object[] searchAfter = processDefinitionResults.getSortValues();
    assertThat(processDefinitionResults.getItems().get(1).getBpmnProcessId())
        .isEqualTo(searchAfter[0].toString());

    processDefinitionResults =
        dao.search(
            new Query<ProcessDefinition>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC))
                .setSize(2)
                .setSearchAfter(searchAfter));

    assertThat(processDefinitionResults.getTotal()).isEqualTo(3);
    assertThat(processDefinitionResults.getItems()).hasSize(1);

    assertThat(processDefinitionResults.getItems().get(0).getBpmnProcessId())
        .isEqualTo(pe3.getBpmnProcessId());
  }
}
