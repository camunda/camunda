/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import com.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.SingleReportEvaluator;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ExportLimitsIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void exportWithLimit() throws Exception {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreRawReportDefinition(
      processInstance.getProcessDefinitionKey(),
      ALL_VERSIONS
    );
    deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.getConfigurationService().setExportCsvLimit(1);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();


    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
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

    embeddedOptimizeRule.getConfigurationService().setExportCsvLimit(highExportCsvLimit);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
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

    embeddedOptimizeRule.getConfigurationService().setExportCsvLimit(highExportCsvLimit);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
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

        final IndexRequest indexRequest = new IndexRequest(
          PROCESS_INSTANCE_INDEX_NAME,
          PROCESS_INSTANCE_INDEX_NAME,
          processInstanceDto.getProcessInstanceId()
        ).source(elasticSearchRule.getObjectMapper().writeValueAsString(processInstanceDto), XContentType.JSON);

        bulkInsert.add(indexRequest);
      }

      elasticSearchRule.getOptimizeElasticClient().bulk(bulkInsert, RequestOptions.DEFAULT);
    }
    elasticSearchRule.refreshAllOptimizeIndices();
  }

  private String createAndStoreRawReportDefinition(String processDefinitionKey,
                                                   String processDefinitionVersion) {
    String id = createNewReportHelper();
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildUpdateSingleProcessReportRequest(id, updatedReport)
        .execute();

    assertThat(response.getStatus(), is(204));
  }


  private String createNewReportHelper() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
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
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

}
