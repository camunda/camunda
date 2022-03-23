/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.TenantDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class TenantImportIT extends AbstractImportIT {

  @Test
  public void tenantIsAvailable() {
    // given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenant";
    engineIntegrationExtension.createTenant(tenantId, tenantName);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(TenantDto.Fields.id.name(), tenantId);
    assertThat(hit.getSourceAsMap()).containsEntry(TenantDto.Fields.name.name(), tenantName);
    assertThat(hit.getSourceAsMap()).containsEntry(TenantDto.Fields.engine.name(), DEFAULT_ENGINE_ALIAS);
  }

  @Test
  public void doNotImportTenantsThatAreExcludedInTheConfiguration() {
    // given
    String tenant1 = "tenantExcluded";
    embeddedOptimizeExtension.getDefaultEngineConfiguration()
      .setExcludedTenants(List.of(tenant1));
    embeddedOptimizeExtension.reloadConfiguration();
    engineIntegrationExtension.createTenant(tenant1);
    engineIntegrationExtension.createTenant("tenant2");
    engineIntegrationExtension.createTenant("tenant3");

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<TenantDto> storedDefinitions = elasticSearchIntegrationTestExtension
      .getAllDocumentsOfIndexAs(TENANT_INDEX_NAME, TenantDto.class);
    assertThat(storedDefinitions)
      .hasSize(2)
      .extracting(TenantDto::getId)
      .isEqualTo(List.of("tenant2", "tenant3"));
  }

  @Test
  public void importsAllTenantsEvenIfTotalAmountIsAboveMaxPageSize() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportTenantMaxPageSize(1);
    engineIntegrationExtension.createTenant("tenant1");
    engineIntegrationExtension.createTenant("tenant2");
    engineIntegrationExtension.createTenant("tenant3");

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(3L);
  }

  @Test
  public void tenantNameIsUpdatable() {
    // given
    final String tenantId = "tenantId";
    final String tenantName = "My New Tenan";
    engineIntegrationExtension.createTenant(tenantId, tenantName);

    // when
    importAllEngineEntitiesFromScratch();

    final String newTenantName = "My New Tenant";
    engineIntegrationExtension.updateTenant(tenantId, newTenantName);

    importAllEngineEntitiesFromLastIndex();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(TENANT_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(TenantDto.Fields.id.name(), tenantId);
    assertThat(hit.getSourceAsMap()).containsEntry(TenantDto.Fields.name.name(), newTenantName);
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    engineIntegrationExtension.createTenant("tenantId", "My New Tenant");
    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    // and
    engineIntegrationExtension.createTenant("tenantId2", "My New Tenant 2");
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(embeddedOptimizeExtension.getIndexHandlerRegistry()
                 .getTenantImportIndexHandler(DEFAULT_ENGINE_ALIAS).getImportIndex()).isEqualTo(2L);
  }

}
