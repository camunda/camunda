/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class CustomerOnboadingDataImportIT extends AbstractImportIT {

  private static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "customer_onboarding_test_process_instances.json";
  private static final String CUSTOMER_ONBOARDING_DEFINITION_NAME = "customer_onboarding_en";
  private static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS = Collections.singleton(ProcessInstanceIndex.TENANT_ID);

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
    addDataToOptimize();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_DEFINITION_INDEX_NAME,
      ProcessDefinitionOptimizeDto.class
    )).isEmpty();
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isFalse();
  }

  @Test
  public void dataGetsImportedToOptimize() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_DEFINITION_INDEX_NAME,
      ProcessDefinitionOptimizeDto.class
    )).hasSize(1);
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isTrue();
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName(),
      ProcessDefinitionOptimizeDto.class
    )).hasSize(3);
  }

  protected boolean indexExist(final String indexName) {
    return embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .indexExists(embeddedOptimizeExtension.getOptimizeElasticClient(), indexName);
  }

  private void addDataToOptimize() {
    CustomerOnboardingDataImportService customerOnboardingDataImportService =
      embeddedOptimizeExtension.getApplicationContext()
        .getBean(CustomerOnboardingDataImportService.class);
    customerOnboardingDataImportService.importData(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, 1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
