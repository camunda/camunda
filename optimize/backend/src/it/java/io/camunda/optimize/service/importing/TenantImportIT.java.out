/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;
// import static
// io.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.TenantDto;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class TenantImportIT extends AbstractImportIT {
//
//   @Test
//   public void tenantIsAvailable() {
//     // given
//     final String tenantId = "tenantId";
//     final String tenantName = "My New Tenant";
//     engineIntegrationExtension.createTenant(tenantId, tenantName);
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 TENANT_INDEX_NAME, TenantDto.class))
//         .singleElement()
//         .satisfies(
//             tenant ->
//                 assertThat(tenant)
//                     .extracting(TenantDto::getId, TenantDto::getName, TenantDto::getEngine)
//                     .containsExactly(tenantId, tenantName, DEFAULT_ENGINE_ALIAS));
//   }
//
//   @Test
//   public void doNotImportTenantsThatAreExcludedInTheConfiguration() {
//     // given
//     String tenant1 = "tenantExcluded";
//
// embeddedOptimizeExtension.getDefaultEngineConfiguration().setExcludedTenants(List.of(tenant1));
//     embeddedOptimizeExtension.reloadConfiguration();
//     engineIntegrationExtension.createTenant(tenant1);
//     engineIntegrationExtension.createTenant("tenant2");
//     engineIntegrationExtension.createTenant("tenant3");
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     final List<TenantDto> storedDefinitions =
//         databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//             TENANT_INDEX_NAME, TenantDto.class);
//     assertThat(storedDefinitions)
//         .hasSize(2)
//         .extracting(TenantDto::getId)
//         .isEqualTo(List.of("tenant2", "tenant3"));
//   }
//
//   @Test
//   public void importsAllTenantsEvenIfTotalAmountIsAboveMaxPageSize() {
//     // given
//     embeddedOptimizeExtension.getConfigurationService().setEngineImportTenantMaxPageSize(1);
//     engineIntegrationExtension.createTenant("tenant1");
//     engineIntegrationExtension.createTenant("tenant2");
//     engineIntegrationExtension.createTenant("tenant3");
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(databaseIntegrationTestExtension.getDocumentCountOf(TENANT_INDEX_NAME))
//         .isEqualTo(3L);
//   }
//
//   @Test
//   public void tenantNameIsUpdatable() {
//     // given
//     final String tenantId = "tenantId";
//     final String tenantName = "My New Tenan";
//     engineIntegrationExtension.createTenant(tenantId, tenantName);
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     final String newTenantName = "My New Tenant";
//     engineIntegrationExtension.updateTenant(tenantId, newTenantName);
//
//     importAllEngineEntitiesFromLastIndex();
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 TENANT_INDEX_NAME, TenantDto.class))
//         .singleElement()
//         .satisfies(
//             tenant ->
//                 assertThat(tenant)
//                     .extracting(TenantDto::getId, TenantDto::getName)
//                     .containsExactly(tenantId, newTenantName));
//   }
//
//   @Test
//   public void afterRestartOfOptimizeAlsoNewDataIsImported() {
//     // given
//     startAndUseNewOptimizeInstance();
//     engineIntegrationExtension.createTenant("tenantId", "My New Tenant");
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     startAndUseNewOptimizeInstance();
//
//     // and
//     engineIntegrationExtension.createTenant("tenantId2", "My New Tenant 2");
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(
//             embeddedOptimizeExtension
//                 .getIndexHandlerRegistry()
//                 .getTenantImportIndexHandler(DEFAULT_ENGINE_ALIAS)
//                 .getImportIndex())
//         .isEqualTo(2L);
//   }
// }
