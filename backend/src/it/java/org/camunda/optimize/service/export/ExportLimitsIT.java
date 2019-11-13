/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import com.opencsv.CSVReader;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.SingleReportEvaluator;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsByteArray;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ExportLimitsIT extends AbstractIT {

  @Test
  public void exportWithLimit() throws Exception {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreRawReportDefinition(
      processInstance.getProcessDefinitionKey(),
      ALL_VERSIONS
    );
    deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeExtension.getConfigurationService().setExportCsvLimit(1);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();


    assertThat(response.getStatus(), is(200));
    byte[] result = getResponseContentAsByteArray(response);
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    assertThat(reader.readAll().size(), is(2));
    reader.close();
  }


  @Test
  public void exportWithBiggerThanDefaultReportLimit() throws Exception {
    final int highExportCsvLimit = SingleReportEvaluator.DEFAULT_RECORD_LIMIT + 1;
    final String processDefinitionKey = "FAKE";
    final String reportId = createAndStoreRawReportDefinition(processDefinitionKey, ALL_VERSIONS);

    // instance count is higher than limit to ensure limit is enforced
    final int instanceCount = 2 * highExportCsvLimit;
    addProcessInstancesToElasticsearch(instanceCount, processDefinitionKey);

    embeddedOptimizeExtension.getConfigurationService().setExportCsvLimit(highExportCsvLimit);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    assertThat(response.getStatus(), is(200));
    byte[] result = getResponseContentAsByteArray(response);
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    // +1 one due to CSV header line
    assertThat(reader.readAll().size(), is(highExportCsvLimit + 1));
    reader.close();
  }


  @Test
  public void exportWithBiggerThanDefaultElasticsearchPageLimit() throws Exception {
    final int highExportCsvLimit = ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT + 1;

    final String processDefinitionKey = "FAKE";
    final String reportId = createAndStoreRawReportDefinition(processDefinitionKey, ALL_VERSIONS);

    // instance count is higher than limit to ensure limit is enforced
    final int instanceCount = 2 * highExportCsvLimit;
    addProcessInstancesToElasticsearch(instanceCount, processDefinitionKey);

    embeddedOptimizeExtension.getConfigurationService().setExportCsvLimit(highExportCsvLimit);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    assertThat(response.getStatus(), is(200));
    byte[] result = getResponseContentAsByteArray(response);
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    // +1 one due to CSV header line
    assertThat(reader.readAll().size(), is(highExportCsvLimit + 1));
    reader.close();
  }

  private void addProcessInstancesToElasticsearch(final int totalInstanceCount, final String processDefinitionKey)
    throws IOException {
    final int maxBulkSize = 10000;
    final int batchCount = Double.valueOf(Math.ceil((double) totalInstanceCount / maxBulkSize)).intValue();

    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(processDefinitionKey);
    processInstanceDto.setProcessDefinitionVersion("1");

    for (int i = 0; i < batchCount; i++) {
      final BulkRequest bulkInsert = new BulkRequest();
      final int alreadyInsertedInstanceCount = i * maxBulkSize;
      final int endOfThisBatchCount = alreadyInsertedInstanceCount + maxBulkSize;
      for (int j = alreadyInsertedInstanceCount; j < endOfThisBatchCount && j < totalInstanceCount; j++) {
        processInstanceDto.setProcessInstanceId(UUID.randomUUID().toString());

        final IndexRequest indexRequest =
          new IndexRequest(PROCESS_INSTANCE_INDEX_NAME)
            .id(processInstanceDto.getProcessInstanceId())
            .source(
              elasticSearchIntegrationTestExtension.getObjectMapper().writeValueAsString(processInstanceDto),
              XContentType.JSON
          );

        bulkInsert.add(indexRequest);
      }

      elasticSearchIntegrationTestExtension.getOptimizeElasticClient().bulk(bulkInsert, RequestOptions.DEFAULT);
    }
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private String createAndStoreRawReportDefinition(String processDefinitionKey,
                                                   String processDefinitionVersion) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setId("something");
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner("something");
    return createNewReport(singleProcessReportDefinitionDto);
  }

  private String createNewReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

}
