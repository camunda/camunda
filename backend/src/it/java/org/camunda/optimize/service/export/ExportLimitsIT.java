/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.export;

import com.opencsv.CSVReader;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsByteArray;
import static org.camunda.optimize.service.export.CsvExportService.DEFAULT_RECORD_LIMIT;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ExportLimitsIT extends AbstractIT {

  @Test
  public void exportWithLimit() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreRawReportDefinition(
      processInstance.getProcessDefinitionKey(),
      ALL_VERSIONS
    );
    deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeExtension.getConfigurationService().getCsvConfiguration().setExportCsvLimit(1);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    byte[] result = getResponseContentAsByteArray(response);
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    assertThat(reader.readAll()).hasSize(2);
    reader.close();
  }


  @Test
  public void exportWithBiggerThanDefaultReportLimit() throws Exception {
    // given
    final int highExportCsvLimit = DEFAULT_RECORD_LIMIT + 1;
    final String processDefinitionKey = "FAKE";
    final String reportId = createAndStoreRawReportDefinition(processDefinitionKey, ALL_VERSIONS);

    // instance count is higher than limit to ensure limit is enforced
    final int instanceCount = 2 * highExportCsvLimit;
    addProcessInstancesToElasticsearch(instanceCount, processDefinitionKey);

    // the CSV export limit is higher than the default record export limit
    embeddedOptimizeExtension.getConfigurationService().getCsvConfiguration().setExportCsvLimit(highExportCsvLimit);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    byte[] result = getResponseContentAsByteArray(response);
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    // +1 one due to CSV header line
    assertThat(reader.readAll()).hasSize(highExportCsvLimit + 1);
    reader.close();
  }


  @Test
  public void exportWithBiggerThanDefaultElasticsearchPageLimit() throws Exception {
    // given
    final int highExportCsvLimit = ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT + 1;

    final String processDefinitionKey = "FAKE";
    final String reportId = createAndStoreRawReportDefinition(processDefinitionKey, ALL_VERSIONS);

    // instance count is higher than limit to ensure limit is enforced
    final int instanceCount = 2 * highExportCsvLimit;
    addProcessInstancesToElasticsearch(instanceCount, processDefinitionKey);

    // the CSV export limit is higher than the max response limit
    embeddedOptimizeExtension.getConfigurationService().getCsvConfiguration().setExportCsvLimit(highExportCsvLimit);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    byte[] result = getResponseContentAsByteArray(response);
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    // the header row makes up the difference
    assertThat(reader.readAll()).hasSize(highExportCsvLimit + 1);
    reader.close();
  }

  private void addProcessInstancesToElasticsearch(final int totalInstanceCount,
                                                  final String processDefinitionKey) throws IOException {
    final int maxBulkSize = 10000;
    final int batchCount = Double.valueOf(Math.ceil((double) totalInstanceCount / maxBulkSize)).intValue();

    final ProcessInstanceDto processInstanceDto = ProcessInstanceDto.builder()
      .processDefinitionKey(processDefinitionKey)
      .processDefinitionVersion("1")
      .build();

    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new ProcessInstanceIndex(processDefinitionKey)
    );

    for (int i = 0; i < batchCount; i++) {
      final BulkRequest bulkInsert = new BulkRequest();
      final int alreadyInsertedInstanceCount = i * maxBulkSize;
      final int endOfThisBatchCount = alreadyInsertedInstanceCount + maxBulkSize;
      for (int j = alreadyInsertedInstanceCount; j < endOfThisBatchCount && j < totalInstanceCount; j++) {
        processInstanceDto.setProcessInstanceId(UUID.randomUUID().toString());

        final IndexRequest indexRequest =
          new IndexRequest(getProcessInstanceIndexAliasName(processDefinitionKey))
            .id(processInstanceDto.getProcessInstanceId())
            .source(
              elasticSearchIntegrationTestExtension.getObjectMapper().writeValueAsString(processInstanceDto),
              XContentType.JSON
            );

        bulkInsert.add(indexRequest);
      }

      elasticSearchIntegrationTestExtension.getOptimizeElasticClient().bulk(bulkInsert);
    }
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private String createAndStoreRawReportDefinition(String processDefinitionKey,
                                                   String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setId("something");
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner("something");
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(getSimpleBpmnDiagram(), new HashMap<>());
  }

}
