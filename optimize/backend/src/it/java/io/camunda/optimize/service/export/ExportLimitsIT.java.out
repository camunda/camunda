/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.export;
//
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static io.camunda.optimize.rest.RestTestUtil.getResponseContentAsByteArray;
// import static io.camunda.optimize.service.export.CsvExportService.DEFAULT_RECORD_LIMIT;
// import static io.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.opencsv.CSVReader;
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.DatabaseConstants;
// import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
// import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import jakarta.ws.rs.core.Response;
// import java.io.ByteArrayInputStream;
// import java.io.IOException;
// import java.io.InputStreamReader;
// import java.time.OffsetDateTime;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.Set;
// import java.util.UUID;
// import org.junit.jupiter.api.Test;
//
// public class ExportLimitsIT extends AbstractPlatformIT {
//
//   @Test
//   public void exportWithLimit() throws Exception {
//     // given
//     final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//     final String reportId =
//         createAndStoreRawReportDefinition(processInstance.getProcessDefinitionKey(),
// ALL_VERSIONS);
//     deployAndStartSimpleProcess();
//     deployAndStartSimpleProcess();
//
//
// embeddedOptimizeExtension.getConfigurationService().getCsvConfiguration().setExportCsvLimit(1);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");
//
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//     final byte[] result = getResponseContentAsByteArray(response);
//     final CSVReader reader = new CSVReader(new InputStreamReader(new
// ByteArrayInputStream(result)));
//
//     // then
//     assertThat(reader.readAll()).hasSize(2);
//     reader.close();
//   }
//
//   @Test
//   public void exportWithBiggerThanDefaultReportLimit() throws Exception {
//     // given
//     final int highExportCsvLimit = DEFAULT_RECORD_LIMIT + 1;
//     final String processDefinitionKey = "FAKE";
//     final String reportId = createAndStoreRawReportDefinition(processDefinitionKey,
// ALL_VERSIONS);
//
//     // instance count is higher than limit to ensure limit is enforced
//     final int instanceCount = 2 * highExportCsvLimit;
//     addProcessInstancesToDatabase(instanceCount, processDefinitionKey);
//
//     // the CSV export limit is higher than the default record export limit
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCsvConfiguration()
//         .setExportCsvLimit(highExportCsvLimit);
//
//     // when
//     final Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");
//
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//     final byte[] result = getResponseContentAsByteArray(response);
//     final CSVReader reader = new CSVReader(new InputStreamReader(new
// ByteArrayInputStream(result)));
//
//     // then
//     // +1 one due to CSV header line
//     assertThat(reader.readAll()).hasSize(highExportCsvLimit + 1);
//     reader.close();
//   }
//
//   @Test
//   public void exportWithBiggerThanDefaultPageLimit() throws Exception {
//     // given
//     final int highExportCsvLimit = DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT + 1;
//     final ProcessDefinitionEngineDto processDefinitionEngineDto =
// deploySimpleProcessDefinition();
//     importAllEngineEntitiesFromScratch();
//     final String reportId =
//         createAndStoreRawReportDefinition(processDefinitionEngineDto.getKey(), ALL_VERSIONS);
//
//     // instance count is higher than limit to ensure limit is enforced
//     final int instanceCount = 2 * highExportCsvLimit;
//     addProcessInstancesToDatabase(instanceCount, processDefinitionEngineDto.getKey());
//     // the CSV export limit is higher than the max response limit
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCsvConfiguration()
//         .setExportCsvLimit(highExportCsvLimit);
//
//     // when
//     final Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");
//
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//     final byte[] result = getResponseContentAsByteArray(response);
//     final CSVReader reader = new CSVReader(new InputStreamReader(new
// ByteArrayInputStream(result)));
//
//     // then
//     // the header row makes up the difference
//     assertThat(reader.readAll()).hasSize(highExportCsvLimit + 1);
//     reader.close();
//   }
//
//   private void addProcessInstancesToDatabase(
//       final int totalInstanceCount, final String processDefinitionKey) throws IOException {
//     final int maxBulkSize = 10000;
//     final int batchCount =
//         Double.valueOf(Math.ceil((double) totalInstanceCount / maxBulkSize)).intValue();
//
//     final ProcessInstanceDto processInstanceDto =
//         ProcessInstanceDto.builder()
//             .processDefinitionKey(processDefinitionKey)
//             .processDefinitionVersion("1")
//             .build();
//
//     databaseIntegrationTestExtension.createMissingIndices(
//         IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX,
//         Collections.emptySet(),
//         Set.of(processDefinitionKey));
//
//     final HashMap<String, Object> entriesToAdd = new HashMap<>();
//
//     for (int i = 0; i < batchCount; i++) {
//       final int alreadyInsertedInstanceCount = i * maxBulkSize;
//       final int endOfThisBatchCount = alreadyInsertedInstanceCount + maxBulkSize;
//       for (int j = alreadyInsertedInstanceCount;
//           j < endOfThisBatchCount && j < totalInstanceCount;
//           j++) {
//         processInstanceDto.setProcessInstanceId(UUID.randomUUID().toString());
//         entriesToAdd.put(processInstanceDto.getProcessInstanceId(), processInstanceDto);
//       }
//
//       databaseIntegrationTestExtension.addEntriesToDatabase(
//           ProcessInstanceIndex.constructIndexName(processDefinitionKey), entriesToAdd);
//     }
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//   }
//
//   private String createAndStoreRawReportDefinition(
//       final String processDefinitionKey, final String processDefinitionVersion) {
//     final ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processDefinitionKey)
//             .setProcessDefinitionVersion(processDefinitionVersion)
//             .setReportDataType(ProcessReportDataType.RAW_DATA)
//             .build();
//     final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
//         new SingleProcessReportDefinitionRequestDto();
//     singleProcessReportDefinitionDto.setData(reportData);
//     singleProcessReportDefinitionDto.setId("something");
//     singleProcessReportDefinitionDto.setLastModifier("something");
//     singleProcessReportDefinitionDto.setName("something");
//     final OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
//     singleProcessReportDefinitionDto.setCreated(someDate);
//     singleProcessReportDefinitionDto.setLastModified(someDate);
//     singleProcessReportDefinitionDto.setOwner("something");
//     return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
//   }
//
//   private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
//     return engineIntegrationExtension.deployAndStartProcessWithVariables(
//         getSimpleBpmnDiagram(), new HashMap<>());
//   }
//
//   private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
//     return
// engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram());
//   }
// }
