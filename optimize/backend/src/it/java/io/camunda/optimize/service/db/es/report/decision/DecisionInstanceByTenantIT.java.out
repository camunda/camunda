/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.decision;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionInstanceByTenantIT extends AbstractDecisionDefinitionIT {
//
//   @Test
//   public void reportAcrossTenants_tenantDefinition_singleTenantSelected() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     testTenantFiltering(
//         Arrays.asList(null, tenantId1, tenantId2), Collections.singletonList(tenantId1));
//   }
//
//   @Test
//   public void reportAcrossTenants_tenantDefinition_noneTenantSelected() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     testTenantFiltering(Arrays.asList(null, tenantId1, tenantId2),
// Collections.singletonList(null));
//   }
//
//   @Test
//   public void reportAcrossTenants_tenantDefinition_multipleTenantsSelected() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     testTenantFiltering(
//         Arrays.asList(null, tenantId1, tenantId2), Arrays.asList(tenantId1, tenantId2));
//   }
//
//   @Test
//   public void reportAcrossTenants_tenantDefinition_multipleTenantsSelectedIncludingNone() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     testTenantFiltering(
//         Arrays.asList(null, tenantId1, tenantId2), Arrays.asList(null, tenantId1, tenantId2));
//   }
//
//   @Test
//   public void reportAcrossTenants_tenantDefinition_emptyTenantListDefaultsToEmptyTenantList() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     testTenantFiltering(Arrays.asList(null, tenantId1, tenantId2), Collections.emptyList());
//   }
//
//   private void testTenantFiltering(
//       final List<String> deployedTenants, final List<String> selectedTenants) {
//     testTenantFiltering(deployedTenants, selectedTenants, selectedTenants);
//   }
//
//   private void testTenantFiltering(
//       final List<String> deployedTenants,
//       final List<String> selectedTenants,
//       final List<String> expectedTenants) {
//     // given
//     final String decisionDefinitionKey = deployAndStartMultiTenantDefinition(deployedTenants);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionKey)
//             .setDecisionDefinitionVersion(ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.RAW_DATA)
//             .build();
//     reportData.setTenantIds(selectedTenants);
//     importAllEngineEntitiesFromScratch();
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo((long) expectedTenants.size());
//     assertThat(result.getData())
//         .extracting(RawDataDecisionInstanceDto::getTenantId)
//         .allSatisfy(tenantId -> assertThat(tenantId).isIn(expectedTenants));
//   }
// }
