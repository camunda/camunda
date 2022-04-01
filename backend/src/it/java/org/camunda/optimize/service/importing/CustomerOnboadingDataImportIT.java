/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class CustomerOnboadingDataImportIT extends AbstractImportIT {

  private static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "customer_onboarding_test_process_instances.json";
  private static final String CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME = "customer_onboarding_definition.json";
  private static final String CUSTOMER_ONBOARDING_DEFINITION_NAME = "customer_onboarding_en";

  @RegisterExtension
  @Order(1)
  private final LogCapturer logCapturer = LogCapturer.create()
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
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isFalse();
  }

  @Test
  public void dataGetsImportedToOptimize() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);

    // then
    List<ProcessDefinitionOptimizeDto> processDefinitionDocuments =
      elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME,
        ProcessDefinitionOptimizeDto.class
      );
    assertThat(processDefinitionDocuments).hasSize(1);
    assertThat(indexExist(new ProcessInstanceIndex(processDefinitionDocuments.get(0)
                                                     .getKey()).getIndexName())).isTrue();
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      new ProcessInstanceIndex(processDefinitionDocuments.get(0).getKey()).getIndexName(),
      ProcessDefinitionOptimizeDto.class
    )).hasSize(3);
  }

  @Test
  public void processDefinitionFileDoesntExist() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, "doesntexist");

    // then
    List<ProcessDefinitionOptimizeDto> processDefinitionDocuments =
      elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME,
        ProcessDefinitionOptimizeDto.class
      );
    assertThat(processDefinitionDocuments).isEmpty();
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isFalse();
    logCapturer.assertContains("Process definition could not be loaded. Please validate your json file.");
  }

  @Test
  public void processInstanceFileDoesntExist() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize("doesntExist", CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);

    // then
    List<ProcessDefinitionOptimizeDto> processDefinitionDocuments =
      elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME,
        ProcessDefinitionOptimizeDto.class
      );
    assertThat(processDefinitionDocuments).hasSize(1);
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isFalse();
    logCapturer.assertContains(
      "Could not load customer onboarding process instances. Please validate the process instance json file.");
  }

  @Test
  public void processInstanceDataAreInvalid() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize("invalid_data.json", CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME);

    // then
    List<ProcessDefinitionOptimizeDto> processDefinitionDocuments =
      elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME,
        ProcessDefinitionOptimizeDto.class
      );
    assertThat(processDefinitionDocuments).hasSize(1);
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isFalse();
    logCapturer.assertContains(
      "Could not load customer onboarding process instances. Please validate the process instance json file.");
  }

  @Test
  public void processDefinitionDataAreInvalid() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize(CUSTOMER_ONBOARDING_PROCESS_INSTANCES, "invalid_data.json");

    // then
    List<ProcessDefinitionOptimizeDto> processDefinitionDocuments =
      elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME,
        ProcessDefinitionOptimizeDto.class
      );
    assertThat(processDefinitionDocuments).isEmpty();
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isFalse();
    logCapturer.assertContains("Process definition could not be loaded. Please validate your json file.");
  }

  protected boolean indexExist(final String indexName) {
    return embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .indexExists(embeddedOptimizeExtension.getOptimizeElasticClient(), indexName);
  }

  private void addDataToOptimize(final String processInstanceFile, final String processDefinitionFile) {
    CustomerOnboardingDataImportService customerOnboardingDataImportService =
      embeddedOptimizeExtension.getApplicationContext()
        .getBean(CustomerOnboardingDataImportService.class);
    customerOnboardingDataImportService.importData(processInstanceFile, processDefinitionFile, 1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
