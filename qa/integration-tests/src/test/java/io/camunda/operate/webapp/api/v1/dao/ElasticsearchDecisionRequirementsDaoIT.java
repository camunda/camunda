/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.NAME;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.RESOURCE_NAME;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.VERSION;
import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ElasticsearchDecisionRequirementsDaoIT extends OperateZeebeAbstractIT {

  @Autowired
  ElasticsearchDecisionRequirementsDao dao;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private RestHighLevelClient esClient;

  private DecisionRequirements decisionRequirements;
  private Long key;
  private List<DecisionRequirements> decisionRequirementsList;
  private Set<Long> keys;
  private String decisionRequirementsXml;
  private Results<DecisionRequirements> decisionRequirementsResults, decisionRequirementsResultsPage1, decisionRequirementsResultsPage2;

  @Test
  public void shouldReturnWhenByKey() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
      SearchHit[] hits = searchAllDocuments(decisionRequirementsIndex.getAlias());
      Map<String, Object> decisionRequirementsDoc = hits[0].getSourceAsMap();
      key = Long.parseLong(decisionRequirementsDoc.get("key").toString());
    });
    when(() -> decisionRequirements = dao.byKey(key));
    then(() -> {
      assertThat(decisionRequirements.getKey()).isEqualTo(key);
      assertThat(decisionRequirements.getDecisionRequirementsId()).isEqualTo("invoiceBusinessDecisions");
      assertThat(decisionRequirements.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void shouldThrowWhenByKeyNotExists() throws Exception {
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
  public void shouldReturnEmptyListWhenByKeysEmpty() throws Exception {
    given(() -> {
    });
    when(() -> decisionRequirementsList = dao.byKeys(Set.of()));
    then(() -> {
      assertThat(decisionRequirementsList).isEmpty();
    });
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNotExist() throws Exception {
    given(() -> {
    });
    when(() -> decisionRequirementsList = dao.byKeys(Set.of(-10L, -20L)));
    then(() -> {
      assertThat(decisionRequirementsList).isEmpty();
    });
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNullKey() throws Exception {
    given(() -> {
    });
    when(() -> decisionRequirementsList = dao.byKeys(Collections.singleton(null)));
    then(() -> {
      assertThat(decisionRequirementsList).isEmpty();
    });
  }

  @Test
  public void shouldReturnEmptyListWhenByKeysNotExistAndNullKey() throws Exception {
    given(() -> {
    });
    when(() -> decisionRequirementsList = dao.byKeys(new HashSet<>(Arrays.asList(-10L, null))));
    then(() -> {
      assertThat(decisionRequirementsList).isEmpty();
    });
  }

  @Test
  public void shouldReturnWhenByKeys() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
          .deployDecision("invoiceBusinessDecisions_v_2.dmn")
          .waitUntil().decisionsAreDeployed(4);
      SearchHit[] hits = searchAllDocuments(decisionRequirementsIndex.getAlias());
      keys = Arrays.stream(hits).map(hit -> Long.parseLong(hit.getSourceAsMap().get("key").toString())).collect(Collectors.toSet());
    });
    when(() -> decisionRequirementsList = dao.byKeys(keys));
    then(() -> {
      assertThat(decisionRequirementsList).hasSize(2);
      assertThat(decisionRequirementsList).extracting(DecisionRequirementsIndex.KEY).containsExactlyInAnyOrder(keys.toArray());
      assertThat(decisionRequirementsList).extracting(DecisionRequirementsIndex.VERSION).containsExactlyInAnyOrder(1, 2);
    });
  }

  @Test
  public void shouldReturnWhenByKeysNullAndNotNull() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
          .deployDecision("invoiceBusinessDecisions_v_2.dmn")
          .waitUntil().decisionsAreDeployed(4);
      SearchHit[] hits = searchAllDocuments(decisionRequirementsIndex.getAlias());
      keys = Arrays.stream(hits).map(hit -> Long.parseLong(hit.getSourceAsMap().get("key").toString())).collect(Collectors.toSet());
    });
    when(() -> {
          Set<Long> keys2 = new HashSet<>(keys);
          keys2.add(null);
          decisionRequirementsList = dao.byKeys(keys2);
        }
    );
    then(() -> {
      assertThat(decisionRequirementsList).hasSize(2);
      assertThat(decisionRequirementsList).extracting(DecisionRequirementsIndex.KEY).containsExactlyInAnyOrder(keys.toArray());
      assertThat(decisionRequirementsList).extracting(DecisionRequirementsIndex.VERSION).containsExactlyInAnyOrder(1, 2);
    });
  }

  @Test
  public void shouldReturnWhenXmlByKey() throws Exception {
    given(() -> {
      tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
          .waitUntil().decisionsAreDeployed(2);
      SearchHit[] hits = searchAllDocuments(decisionRequirementsIndex.getAlias());
      key = Arrays.stream(hits).map(hit -> Long.parseLong(hit.getSourceAsMap().get("key").toString())).findFirst().orElseThrow();
    });
    when(() -> decisionRequirementsXml = dao.xmlByKey(key));
    then(() -> {
      assertThat(decisionRequirementsXml).contains("id=\"invoiceBusinessDecisions\"");
      assertThatIsXML(decisionRequirementsXml);
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
  public void shouldReturnEmptyListWhenNoDecisionRequirementsExist() throws Exception {
    given(() -> { /*"no decision requirements"*/ });
    when(() -> decisionRequirementsResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(decisionRequirementsResults.getItems()).isEmpty();
      assertThat(decisionRequirementsResults.getTotal()).isZero();
    });
  }

  @Test
  public void shouldReturnNonEmptyListWhenDecisionRequirementsExist() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> decisionRequirementsResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
      assertThat(decisionRequirementsResults.getItems()).extracting(DECISION_REQUIREMENTS_ID)
          .containsExactly("invoiceBusinessDecisions", "invoiceBusinessDecisions");
      assertThat(decisionRequirementsResults.getItems()).extracting(VERSION).containsExactly(1, 2);
      assertThat(decisionRequirementsResults.getItems()).extracting(RESOURCE_NAME)
          .containsExactly("invoiceBusinessDecisions_v_1.dmn", "invoiceBusinessDecisions_v_2.dmn");
    });
  }

  @Test
  public void shouldPageWithSearchAfterSizeAndSortedAsc() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      decisionRequirementsResultsPage1 = dao.search(new Query<DecisionRequirements>().setSize(1)
          .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.ASC)));
      decisionRequirementsResultsPage2 = dao.search(new Query<DecisionRequirements>().setSize(1)
          .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.ASC))
          .setSearchAfter(new Object[] { decisionRequirementsResultsPage1.getItems().get(0).getResourceName(),
              decisionRequirementsResultsPage1.getItems().get(0).getKey() }));
    });
    then(() -> {
      assertThat(decisionRequirementsResultsPage1.getTotal()).isEqualTo(2);
      assertThat(decisionRequirementsResultsPage1.getItems()).hasSize(1);
      assertThat(decisionRequirementsResultsPage1.getItems()).extracting(RESOURCE_NAME).containsExactly("invoiceBusinessDecisions_v_1.dmn");
      assertThat(decisionRequirementsResultsPage1.getItems()).extracting(VERSION).containsExactly(1);
      assertThat(decisionRequirementsResultsPage2.getTotal()).isEqualTo(2);
      assertThat(decisionRequirementsResultsPage2.getItems()).hasSize(1);
      assertThat(decisionRequirementsResultsPage2.getItems()).extracting(RESOURCE_NAME).containsExactly("invoiceBusinessDecisions_v_2.dmn");
      assertThat(decisionRequirementsResultsPage2.getItems()).extracting(VERSION).containsExactly(2);
    });
  }

  @Test
  public void shouldPageWithSearchAfterSizeAndSortedDesc() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      decisionRequirementsResultsPage1 = dao.search(new Query<DecisionRequirements>().setSize(1)
          .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC)));
      decisionRequirementsResultsPage2 = dao.search(new Query<DecisionRequirements>().setSize(1)
          .setSort(Query.Sort.listOf(RESOURCE_NAME, Query.Sort.Order.DESC))
          .setSearchAfter(new Object[] { decisionRequirementsResultsPage1.getItems().get(0).getResourceName(),
              decisionRequirementsResultsPage1.getItems().get(0).getKey() }));
    });
    then(() -> {
      assertThat(decisionRequirementsResultsPage1.getTotal()).isEqualTo(2);
      assertThat(decisionRequirementsResultsPage1.getItems()).hasSize(1);
      assertThat(decisionRequirementsResultsPage1.getItems()).extracting(RESOURCE_NAME)
          .containsExactly("invoiceBusinessDecisions_v_2.dmn");
      assertThat(decisionRequirementsResultsPage1.getItems()).extracting(VERSION).containsExactly(2);
      assertThat(decisionRequirementsResultsPage2.getTotal()).isEqualTo(2);
      assertThat(decisionRequirementsResultsPage2.getItems()).hasSize(1);
      assertThat(decisionRequirementsResultsPage2.getItems()).extracting(RESOURCE_NAME).containsExactly("invoiceBusinessDecisions_v_1.dmn");
      assertThat(decisionRequirementsResultsPage2.getItems()).extracting(VERSION).containsExactly(1);
    });
  }

  @Test
  public void shouldFilterByFieldAndSortDesc() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      final DecisionRequirements decisionRequirementsFilter = new DecisionRequirements().setName("Invoice Business Decisions");
      decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
          .setFilter(decisionRequirementsFilter)
          .setSort(Query.Sort.listOf(VERSION, Query.Sort.Order.DESC)));
    });
    then(() -> {
      assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
      List<DecisionRequirements> decisionRequirements = decisionRequirementsResults.getItems();
      assertThat(decisionRequirements).hasSize(2);
      assertThat(decisionRequirements).extracting(NAME).containsExactly("Invoice Business Decisions", "Invoice Business Decisions");
      assertThat(decisionRequirements).extracting(VERSION).containsExactly(2, 1);
    });
  }

  @Test
  public void shouldFilterByMultipleFields() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      final DecisionRequirements decisionRequirementsFilter = new DecisionRequirements().setName("Invoice Business Decisions").setVersion(2);
      decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
          .setFilter(decisionRequirementsFilter));
    });
    then(() -> {
      assertThat(decisionRequirementsResults.getTotal()).isEqualTo(1);
      List<DecisionRequirements> decisionRequirements = decisionRequirementsResults.getItems();
      assertThat(decisionRequirements).hasSize(1);
      assertThat(decisionRequirements).extracting(NAME).containsExactly("Invoice Business Decisions");
      assertThat(decisionRequirements).extracting(VERSION).containsExactly(2);
    });
  }

  @Test
  public void shouldFilterAndPageAndSort() throws Exception {
    given(() -> tester.deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil().decisionsAreDeployed(4));
    when(() -> {
      final DecisionRequirements decisionRequirementsFilter = new DecisionRequirements().setName("Invoice Business Decisions");
      decisionRequirementsResults = dao.search(new Query<DecisionRequirements>()
          .setFilter(decisionRequirementsFilter)
          .setSort(Query.Sort.listOf(VERSION, Query.Sort.Order.DESC))
          .setSize(1));
    });
    then(() -> {
      assertThat(decisionRequirementsResults.getTotal()).isEqualTo(2);
      List<DecisionRequirements> decisionRequirements = decisionRequirementsResults.getItems();
      assertThat(decisionRequirements).hasSize(1);
      assertThat(decisionRequirements).extracting(NAME).containsExactly("Invoice Business Decisions");
      assertThat(decisionRequirements).extracting(VERSION).containsExactly(2);
    });
  }

  protected SearchHit[] searchAllDocuments(String index) {
    SearchRequest searchRequest = new SearchRequest(index).source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()));
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getHits();
    } catch (IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
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
