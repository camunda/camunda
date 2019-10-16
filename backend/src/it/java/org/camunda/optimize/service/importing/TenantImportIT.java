/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TenantImportIT extends AbstractImportIT {

  @Test
  public void tenantIsAvailable() throws IOException {
    //given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenant";
    engineIntegrationExtensionRule.createTenant(tenantId, tenantName);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.id.name()), is(tenantId));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.name.name()), is(tenantName));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.engine.name()), is(DEFAULT_ENGINE_ALIAS));
  }

  @Test
  public void tenantNameIsUpdatable() throws IOException {
    //given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenan";
    engineIntegrationExtensionRule.createTenant(tenantId, tenantName);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    final String newTenantName = "My New Tenant";
    engineIntegrationExtensionRule.updateTenant(tenantId, newTenantName);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.id.name()), is(tenantId));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.name.name()), is(newTenantName));
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    engineIntegrationExtensionRule.createTenant("tenantId", "My New Tenant");
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();

    // and
    engineIntegrationExtensionRule.createTenant("tenantId2", "My New Tenant 2");
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    assertThat(
      embeddedOptimizeExtensionRule.getIndexProvider().getTenantImportIndexHandler(DEFAULT_ENGINE_ALIAS).getImportIndex(),
      is(2L)
    );
  }

}
