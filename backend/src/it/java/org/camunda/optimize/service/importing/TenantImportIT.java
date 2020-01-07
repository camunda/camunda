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

import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TenantImportIT extends AbstractImportIT {

  @Test
  public void tenantIsAvailable() {
    //given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenant";
    engineIntegrationExtension.createTenant(tenantId, tenantName);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.id.name()), is(tenantId));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.name.name()), is(tenantName));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.engine.name()), is(DEFAULT_ENGINE_ALIAS));
  }

  @Test
  public void tenantNameIsUpdatable() {
    //given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenan";
    engineIntegrationExtension.createTenant(tenantId, tenantName);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String newTenantName = "My New Tenant";
    engineIntegrationExtension.updateTenant(tenantId, newTenantName);

    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.id.name()), is(tenantId));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.name.name()), is(newTenantName));
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    engineIntegrationExtension.createTenant("tenantId", "My New Tenant");
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    // and
    engineIntegrationExtension.createTenant("tenantId2", "My New Tenant 2");
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(
      embeddedOptimizeExtension.getIndexProvider().getTenantImportIndexHandler(DEFAULT_ENGINE_ALIAS).getImportIndex(),
      is(2L)
    );
  }

}
