/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class CustomerOnboadingDataImportIT extends AbstractImportIT {

  public static final String CUSTOMER_ONBOARDING_PROCESS_INSTANCES = "customer_onboarding_test_process_instances.json";
  public static final String CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME = "customer_onboarding_definition.json";
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
    assertThat(indexExist(new ProcessInstanceIndex(processDefinitionDocuments.get(0).getKey()).getIndexName())).isTrue();
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      new ProcessInstanceIndex(processDefinitionDocuments.get(0).getKey()).getIndexName(),
      ProcessDefinitionOptimizeDto.class
    )).hasSize(3);
    List<ProcessInstanceDto> processInstanceDtos = elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      new ProcessInstanceIndex(processDefinitionDocuments.get(0).getKey()).getIndexName(), ProcessInstanceDto.class);
    assertThat(processInstanceDtos).anyMatch(processInstanceDto -> processInstanceDto.getIncidents() != null);
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
      "Could not load customer onboarding process instances to input stream. Please validate the process instance json file.");
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
      "Could not load customer onboarding process instances to input stream. Please validate the process instance json file.");
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

  @Test
  public void verifyProcessDatesAreUpToDate() {
    // given
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final OffsetDateTime instanceStartDate = OffsetDateTime.parse("2022-02-04T21:24:14+01:00", ISO_OFFSET_DATE_TIME);
    final OffsetDateTime instanceEndDate = OffsetDateTime.parse("2022-02-04T21:25:18+01:00", ISO_OFFSET_DATE_TIME);
    final OffsetDateTime flowNodeStartDate = OffsetDateTime.parse("2022-02-04T21:24:15+01:00", ISO_OFFSET_DATE_TIME);
    final OffsetDateTime flowNodeEndDate = OffsetDateTime.parse("2022-02-04T21:24:16+01:00", ISO_OFFSET_DATE_TIME);
    final long offset = ChronoUnit.SECONDS.between(instanceEndDate, now);
    final OffsetDateTime newStartDate = instanceStartDate.plusSeconds(offset);
    final OffsetDateTime newEndDate = instanceEndDate.plusSeconds(offset);
    final OffsetDateTime newFlowNodeStartDate = flowNodeStartDate.plusSeconds(offset);
    final OffsetDateTime newFlowNodeEndDate = flowNodeEndDate.plusSeconds(offset);
    embeddedOptimizeExtension.getConfigurationService().setCustomerOnboardingImport(true);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    addDataToOptimize(
      "customer_onboarding_process_instance_date_modification.json",
      CUSTOMER_ONBOARDING_DEFINITION_FILE_NAME
    );

    // then
    List<ProcessDefinitionOptimizeDto> processDefinitionDocuments =
      elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME,
        ProcessDefinitionOptimizeDto.class
      );
    assertThat(processDefinitionDocuments).hasSize(1);
    assertThat(indexExist(new ProcessInstanceIndex(CUSTOMER_ONBOARDING_DEFINITION_NAME).getIndexName())).isTrue();
    List<ProcessInstanceDto> processInstanceDto = elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      new ProcessInstanceIndex(processDefinitionDocuments.get(0).getKey()).getIndexName(), ProcessInstanceDto.class);

    assertThat(processInstanceDto)
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getFlowNodeInstances()).singleElement().satisfies(flowNode ->  {
          assertThat(flowNode.getStartDate()).isEqualTo(newFlowNodeStartDate);
          assertThat(flowNode.getEndDate()).isEqualTo(newFlowNodeEndDate);
        });
        assertThat(instance.getStartDate()).isEqualTo(newStartDate);
        assertThat(instance.getEndDate()).isEqualTo(newEndDate);
      });
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
