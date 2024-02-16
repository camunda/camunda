/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.RESOURCE_NAME;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.VERSION;
import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecisionRequirementsDaoIT extends OperateSearchAbstractIT {

  @Autowired
  private DecisionRequirementsDao dao;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    String indexName = decisionRequirementsIndex.getFullQualifiedName();

    String resourceXml = testResourceManager.readResourceFileContentsAsString("invoiceBusinessDecisions_v_1.dmn");
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionRequirementsEntity().setId("2251799813685249").setKey(2251799813685249L)
        .setDecisionRequirementsId("invoiceBusinessDecisions").setName("Invoice Business Decisions")
        .setVersion(1).setResourceName("invoiceBusinessDecisions_v_1.dmn").setTenantId(DEFAULT_TENANT_ID)
        .setXml(resourceXml));

    resourceXml = testResourceManager.readResourceFileContentsAsString("invoiceBusinessDecisions_v_2.dmn");
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionRequirementsEntity().setId("2251799813685253").setKey(2251799813685253L)
        .setDecisionRequirementsId("invoiceBusinessDecisions").setName("Invoice Business Decisions")
        .setVersion(2).setResourceName("invoiceBusinessDecisions_v_2.dmn").setTenantId(DEFAULT_TENANT_ID)
        .setXml(resourceXml));

    searchContainerManager.refreshIndices("*operate-decision*");
  }

  @Test
  public void shouldReturnDecisionRequirements() {
    Results<DecisionRequirements> decisionRequirementsResults = dao.search(new Query<>());

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).extracting(DECISION_REQUIREMENTS_ID)
        .containsExactlyInAnyOrder("invoiceBusinessDecisions", "invoiceBusinessDecisions");
    assertThat(decisionRequirementsResults.getItems()).extracting(VERSION).containsExactlyInAnyOrder(1, 2);
    assertThat(decisionRequirementsResults.getItems()).extracting(RESOURCE_NAME)
        .containsExactlyInAnyOrder("invoiceBusinessDecisions_v_1.dmn", "invoiceBusinessDecisions_v_2.dmn");
  }

  @Test
  public void shouldSortDecisionRequirementsDesc() {
    Results<DecisionRequirements> decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
        .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC)));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).extracting(RESOURCE_NAME)
        .containsExactly("invoiceBusinessDecisions_v_2.dmn", "invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldSortDecisionRequirementsAsc() {
    Results<DecisionRequirements> decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
        .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.ASC)));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).extracting(RESOURCE_NAME)
        .containsExactly( "invoiceBusinessDecisions_v_1.dmn", "invoiceBusinessDecisions_v_2.dmn");
  }

  @Test
  public void shouldPageDecisionRequirements() {
    Results<DecisionRequirements> decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
        .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC)).setSize(1));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).hasSize(1);
    assertThat(decisionRequirementsResults.getItems().get(0).getResourceName()).isEqualTo("invoiceBusinessDecisions_v_2.dmn");

    Object[] searchAfter = decisionRequirementsResults.getSortValues();

    decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
        .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC)).setSize(1)
        .setSearchAfter(searchAfter));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
    assertThat(decisionRequirementsResults.getItems()).hasSize(1);
    assertThat(decisionRequirementsResults.getItems().get(0).getResourceName()).isEqualTo("invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldFilterDecisionRequirements() {
    Results<DecisionRequirements> decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
        .setFilter(new DecisionRequirements().setResourceName("invoiceBusinessDecisions_v_1.dmn")));

    assertThat(decisionRequirementsResults.getTotal()).isEqualTo(1);

    assertThat(decisionRequirementsResults.getItems().get(0).getResourceName()).isEqualTo("invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldReturnWhenByKey() {
    DecisionRequirements decisionRequirements = dao.byKey(2251799813685249L);

    assertThat(decisionRequirements.getResourceName()).isEqualTo("invoiceBusinessDecisions_v_1.dmn");
  }

  @Test
  public void shouldThrowKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysEmpty() {
    List<DecisionRequirements> decisionRequirementsList = dao.byKeys(Set.of());

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNotExist() {
    List<DecisionRequirements> decisionRequirementsList = dao.byKeys(Set.of(-10L, -20L));

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNullKey() {
    List<DecisionRequirements> decisionRequirementsList = dao.byKeys(Collections.singleton(null));

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNotExistAndNullKey() {
    List<DecisionRequirements> decisionRequirementsList = dao.byKeys(new HashSet<>(Arrays.asList(-10L, null)));

    assertThat(decisionRequirementsList).isEmpty();
  }

  @Test
  public void shouldReturnWhenByKeys() {
    Set<Long> keys = Set.of(2251799813685249L, 2251799813685253L);

    List<DecisionRequirements> decisionRequirementsList = dao.byKeys(keys);

    assertThat(decisionRequirementsList).hasSize(2);
    assertThat(decisionRequirementsList).extracting(DecisionRequirementsIndex.KEY).containsExactlyInAnyOrder(keys.toArray());
    assertThat(decisionRequirementsList).extracting(DecisionRequirementsIndex.VERSION).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  public void shouldReturnWhenXmlByKey() {
    String decisionRequirementsXml = dao.xmlByKey(2251799813685249L);

    assertThat(decisionRequirementsXml).contains("id=\"invoiceBusinessDecisions\"");
    try {
      final InputStream xmlInputStream = new ByteArrayInputStream(decisionRequirementsXml.getBytes(StandardCharsets.UTF_8));
      new XMLUtil().getSAXParserFactory().newSAXParser().parse(xmlInputStream, new DefaultHandler());
    } catch (SAXException | IOException | ParserConfigurationException e) {
      fail(String.format("String '%s' should be of type xml", decisionRequirementsXml), e);
    }
  }

  @Test
  public void shouldThrowXmlKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.xmlByKey(1L));
  }
}
