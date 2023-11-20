/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;

public class CustomerOnboardingDataImportIT extends AbstractImportIT {

  public static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "customer_onboarding_test_process_instances.json";
  public static final String CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME = "customer_onboarding_definition.json";
  private static final String CUSTOMER_ONBOARDING_DEFINITION_NAME = "customer_onboarding_en";

  @RegisterExtension
  @Order(1)
  public final LogCapturer logCapturer = LogCapturer.create()
    .captureForType(CustomerOnboardingDataImportService.class);

  @BeforeEach
  public void cleanUpExistingProcessInstanceIndices() {
    elasticSearchIntegrationTestExtension.deleteAllProcessInstanceIndices();
  }

  @Test
  public void importCanBeDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(false);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_DEFINITION_INDEX_NAME,
      ProcessDefinitionOptimizeDto.class
    )).isEmpty();
    assertThat(indexExist(ProcessInstanceIndex.constructIndexName(CUSTOMER_ONBOARDING_DEFINITION_NAME))).isFalse();
  }

  @Test
  public void dataImportIsSkippedIfEnabledInConfigButRunningInPlatformMode() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_DEFINITION_INDEX_NAME,
      ProcessDefinitionOptimizeDto.class
    )).isEmpty();
    assertThat(indexExist(ProcessInstanceIndex.constructIndexName(CUSTOMER_ONBOARDING_DEFINITION_NAME))).isFalse();
    logCapturer.assertContains(
      "C8 Customer onboarding data enabled but running in Platform mode. Skipping customer onboarding data import");
  }

  protected boolean indexExist(final String indexName) {
    return embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .indexExists(embeddedOptimizeExtension.getOptimizeElasticClient(), indexName);
  }

  private void addDataToOptimize(final String processInstanceFile, final String processDefinitionFile) {
    CustomerOnboardingDataImportService customerOnboardingDataImportService =
      embeddedOptimizeExtension.getBean(CustomerOnboardingDataImportService.class);
    customerOnboardingDataImportService.importData(processInstanceFile, processDefinitionFile, 1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
