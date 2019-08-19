/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;

import java.io.IOException;

import static org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TenantImportIT extends AbstractImportIT {

  @Test
  public void tenantIsAvailable() throws IOException {
    //given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenant";
    engineRule.createTenant(tenantId, tenantName);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(TENANT_INDEX_NAME);
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
    engineRule.createTenant(tenantId, tenantName);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final String newTenantName = "My New Tenant";
    engineRule.updateTenant(tenantId, newTenantName);

    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.id.name()), is(tenantId));
    assertThat(hit.getSourceAsMap().get(TenantDto.Fields.name.name()), is(newTenantName));
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    engineRule.createTenant("tenantId", "My New Tenant");
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    // and
    engineRule.createTenant("tenantId2", "My New Tenant 2");
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    assertThat(
      embeddedOptimizeRule.getIndexProvider().getTenantImportIndexHandler(DEFAULT_ENGINE_ALIAS).getImportIndex(),
      is(2L)
    );
  }

}
