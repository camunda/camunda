/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.TIMESTAMP_OF_LAST_ENTITY;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
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
    embeddedOptimizeExtension.updateImportIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits().value, is(2L));
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
    embeddedOptimizeExtension.updateImportIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");
    assertThat(searchResponse.getHits().getTotalHits().value, is(2L));
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
    embeddedOptimizeExtension.updateImportIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME);

    // then
    Set<String> allowedProcessDefinitionKeys = new HashSet<>();
    allowedProcessDefinitionKeys.add("TestProcess1");
    allowedProcessDefinitionKeys.add("TestProcess2");

    assertImportResults(searchResponse, allowedProcessDefinitionKeys);
  }

  @Test
  public void allProcessInstancesEventAndVariablesAreImportedWithAuthentication() {
    // given
    secondaryEngineIntegrationExtension.addUser("admin", "admin");
    secondaryEngineIntegrationExtension.grantAllAuthorizations("admin");
    addSecureSecondEngineToConfiguration();
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartSimpleProcessDefinitionForAllEngines();

    // when
    embeddedOptimizeExtension.updateImportIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME);

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
    engineIntegrationExtension.createTenant(firstTenantId, tenantName);
    secondaryEngineIntegrationExtension.createTenant(firstTenantId, tenantName);
    secondaryEngineIntegrationExtension.createTenant(secondTenantId, tenantName);

    // when
    embeddedOptimizeExtension.updateImportIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, CoreMatchers.is(2L));
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
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TIMESTAMP_BASED_IMPORT_INDEX_NAME);

    assertThat(searchResponse.getHits().getTotalHits().value, is(18L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String typeName = searchHit.getSourceAsMap().get(ES_TYPE_INDEX_REFERS_TO).toString();
      String timestampOfLastEntity = searchHit.getSourceAsMap().get(TIMESTAMP_OF_LAST_ENTITY).toString();
      OffsetDateTime timestamp = OffsetDateTime.parse(
        timestampOfLastEntity,
        embeddedOptimizeExtension.getDateTimeFormatter()
      );
      assertThat(
        "Timestamp for " + typeName + " should be recent",
        timestamp,
        greaterThan(OffsetDateTime.now().minusHours(1))
      );
    }
  }

  private void assertImportResults(SearchResponse searchResponse, Set<String> allowedProcessDefinitionKeys) {
    assertThat(searchResponse.getHits().getTotalHits().value, is(2L));
    for (SearchHit searchHit : searchResponse.getHits().getHits()) {
      String processDefinitionKey = (String) searchHit.getSourceAsMap().get(ProcessInstanceIndex.PROCESS_DEFINITION_KEY);
      assertThat(allowedProcessDefinitionKeys.contains(processDefinitionKey), is(true));
      allowedProcessDefinitionKeys.remove(processDefinitionKey);
      List events = (List) searchHit.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(2));
      List variables = (List) searchHit.getSourceAsMap().get(VARIABLES);
      //NOTE: independent from process definition
      assertThat(variables.size(), is(1));
    }
  }

}
