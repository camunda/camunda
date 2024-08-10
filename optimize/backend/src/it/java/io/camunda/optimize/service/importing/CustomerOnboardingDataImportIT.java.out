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
// import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.DataImportSourceType;
// import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
// import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
// import io.github.netmikey.logunit.api.LogCapturer;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Order;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.RegisterExtension;
//
// @Tag(OPENSEARCH_PASSING)
// public class CustomerOnboardingDataImportIT extends AbstractImportIT {
//
//   public static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES =
//       "customer_onboarding_test_process_instances.json";
//   public static final String CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME =
//       "customer_onboarding_definition.json";
//   private static final String CUSTOMER_ONBOARDING_DEFINITION_NAME = "customer_onboarding_en";
//
//   @RegisterExtension
//   @Order(1)
//   public final LogCapturer logCapturer =
//       LogCapturer.create().captureForType(CustomerOnboardingDataImportService.class);
//
//   @BeforeEach
//   public void cleanUpExistingProcessInstanceIndices() {
//     databaseIntegrationTestExtension.deleteAllProcessInstanceIndices();
//   }
//
//   @Test
//   public void importCanBeDisabled() {
//     // given
//     embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(false);
//     embeddedOptimizeExtension.reloadConfiguration();
//
//     // when
//     addDataToOptimize(
//         CUSTOMER_ONBOARDING_PROCESS_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 PROCESS_DEFINITION_INDEX_NAME, ProcessDefinitionOptimizeDto.class))
//         .isEmpty();
//     assertThat(
//             indexExist(
//                 ProcessInstanceIndex.constructIndexName(CUSTOMER_ONBOARDING_DEFINITION_NAME)))
//         .isFalse();
//   }
//
//   @Test
//   public void dataImportDataGetsConvertedWithWarningIfEnabledInConfigButRunningInPlatformMode() {
//     // given
//     embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
//     embeddedOptimizeExtension.reloadConfiguration();
//
//     // when
//     addDataToOptimize(
//         CUSTOMER_ONBOARDING_PROCESS_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 PROCESS_DEFINITION_INDEX_NAME, ProcessDefinitionOptimizeDto.class))
//         .singleElement()
//         .satisfies(
//             def -> {
//               assertThat(def.getTenantId()).isNull();
//               assertThat(def.getDataSource().getType()).isEqualTo(DataImportSourceType.ENGINE);
//               assertThat(def.getDataSource().getName()).isEqualTo("camunda-bpm");
//             });
//     assertThat(
//             indexExist(
//                 ProcessInstanceIndex.constructIndexName(CUSTOMER_ONBOARDING_DEFINITION_NAME)))
//         .isTrue();
//     logCapturer.assertContains(
//         "C8 Customer onboarding data enabled but running in Platform mode. Converting data to C7
// test data");
//   }
//
//   protected boolean indexExist(final String indexName) {
//     return embeddedOptimizeExtension
//         .getDatabaseSchemaManager()
//         .indexExists(embeddedOptimizeExtension.getOptimizeDatabaseClient(), indexName);
//   }
//
//   private void addDataToOptimize(
//       final String processInstanceFile, final String processDefinitionFile) {
//     CustomerOnboardingDataImportService customerOnboardingDataImportService =
//         embeddedOptimizeExtension.getBean(CustomerOnboardingDataImportService.class);
//     customerOnboardingDataImportService.importData(processInstanceFile, processDefinitionFile,
// 1);
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//   }
// }
