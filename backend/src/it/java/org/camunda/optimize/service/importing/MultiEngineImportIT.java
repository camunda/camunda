/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.type.ProcessDefinitionType.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_OF_LAST_ENTITY;
import static org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;


public class MultiEngineImportIT extends AbstractMultiEngineIT {

  @Test
  public void allProcessDefinitionXmlsAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(PROC_DEF_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
    }
  }

  @Test
  public void allProcessDefinitionsAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(PROC_DEF_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
    }
  }

  @Test
  public void allProcessInstancesEventAndVariablesAreImported() {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(ElasticsearchConstants.PROC_INSTANCE_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertImportResults(searchResponse, allowedProcessDefinitionKeys);
  }

  @Test
  public void allProcessInstancesEventAndVariablesAreImportedWithAuthentication() {
    // given
    secondEngineRule.addUser("admin", "admin");
    secondEngineRule.grantAllAuthorizations("admin");
    addSecureSecondEngineToConfiguration();
    embeddedOptimizeRule.reloadConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(ElasticsearchConstants.PROC_INSTANCE_TYPE);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertImportResults(searchResponse, allowedProcessDefinitionKeys);
  }

  @Test
  public void allTenantsAreImported() {
    // given
    final String firstTenantId = "tenantId1";
    final String tenantName = "My New Tenant";
    final String secondTenantId = "tenantId2";
    addSecondEngineToConfiguration();
    defaultEngineRule.createTenant(firstTenantId, tenantName);
    secondEngineRule.createTenant(firstTenantId, tenantName);
    secondEngineRule.createTenant(secondTenantId, tenantName);

    // when
    embeddedOptimizeRule.updateImportIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = elasticSearchRule.getSearchResponseForAllDocumentsOfType(TENANT_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), CoreMatchers.is(2L));
    final Map<Object, List<Map<String, Object>>> tenantsByEngine = Arrays.stream(idsResp.getHits().getHits())
      .map(SearchHit::getSourceAsMap)
      .collect(Collectors.groupingBy(o -> o.get(TenantDto.Fields.engine.name())));
    assertThat(tenantsByEngine.keySet(), containsInAnyOrder(DEFAULT_ENGINE_ALIAS, SECOND_ENGINE_ALIAS));
    assertThat(tenantsByEngine.get(DEFAULT_ENGINE_ALIAS).size(), is(1));
    assertThat(tenantsByEngine.get(DEFAULT_ENGINE_ALIAS).get(0).get(TenantDto.Fields.id.name()), is(firstTenantId));
    assertThat(tenantsByEngine.get(SECOND_ENGINE_ALIAS).size(), is(1));
    assertThat(tenantsByEngine.get(SECOND_ENGINE_ALIAS).get(0).get(TenantDto.Fields.id.name()), is(secondTenantId));
  }

  @Test
  public void afterRestartOfOptimizeRightImportIndexIsUsed() throws Exception {
    // given
    addSecondEngineToConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();
    // we need finished user tasks
    deployAndStartUserTaskProcessForAllEngines();
    finishAllUserTasksForAllEngines();
    // as well as running activities
    deployAndStartUserTaskProcessForAllEngines();
    deployAndStartDecisionDefinitionForAllEngines();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse searchResponse = performProcessDefinitionSearchRequest(TIMESTAMP_BASED_IMPORT_INDEX_TYPE);

    assertThat(searchResponse.getHits().getTotalHits(), is(18L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String typeName = searchHit.getSourceAsMap().get(ES_TYPE_INDEX_REFERS_TO).toString();
      String timestampOfLastEntity = searchHit.getSourceAsMap().get(TIMESTAMP_OF_LAST_ENTITY).toString();
      OffsetDateTime timestamp = OffsetDateTime.parse(
        timestampOfLastEntity,
        embeddedOptimizeRule.getDateTimeFormatter()
      );
      assertThat(
        "Timestamp for " + typeName + " should be recent",
        timestamp,
        greaterThan(OffsetDateTime.now().minusHours(1))
      );
    }
  }

  private void assertImportResults(SearchResponse searchResponse, Set<String> allowedProcessDefinitionKeys) {
    assertThat(searchResponse.getHits().getTotalHits(), is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(ProcessInstanceType.PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
      List events = (List) searchHit.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(2));
      List stringVariables = (List) searchHit.getSourceAsMap().get(STRING_VARIABLES);
      //NOTE: independent from process definition
      assertThat(stringVariables.size(), is(1));
    }
  }

  private SearchResponse performProcessDefinitionSearchRequest(String procDefType) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(procDefType)
      .types(procDefType)
      .source(searchSourceBuilder);

    try {
      return elasticSearchRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not query the import count!", e);
    }
  }

}
