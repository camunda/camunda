/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.flownode.frequency.groupby.variable.distributedby.process;
//
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static io.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
// import static io.camunda.optimize.util.BpmnModels.getTwoServiceTasksProcess;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.IdGenerator;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.Collections;
// import java.util.List;
// import org.assertj.core.groups.Tuple;
// import org.junit.jupiter.api.Test;
//
// public class FlowNodeFrequencyByVariableByProcessReportEvaluationIT extends AbstractPlatformIT {
//
//   private static final String STRING_VAR = "stringVar";
//
//   @Test
//   public void reportEvaluationWithSingleProcessDefinitionSource() {
//     // given
//     final ProcessInstanceEngineDto processInstanceDto =
//         engineIntegrationExtension.deployAndStartProcessWithVariables(
//             getTwoServiceTasksProcess("aProcess"),
//             Collections.singletonMap(STRING_VAR, "aStringValue"));
//     importAllEngineEntitiesFromScratch();
//     final String processDisplayName = "processDisplayName";
//     final String processIdentifier = IdGenerator.getNextId();
//     ReportDataDefinitionDto definition =
//         new ReportDataDefinitionDto(
//             processIdentifier, processInstanceDto.getProcessDefinitionKey(), processDisplayName);
//     final ProcessReportDataDto reportData =
//         createReport(List.of(definition), STRING_VAR, VariableType.STRING);
//
//     // when
//     final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>>
//         evaluationResponse = reportClient.evaluateHyperMapReport(reportData);
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
//         evaluationResponse.getResult();
//     final ProcessReportDataDto resultReportDataDto =
//         evaluationResponse.getReportDefinition().getData();
//
//     // then
//     assertThat(resultReportDataDto.getProcessDefinitionKey())
//         .isEqualTo(processInstanceDto.getProcessDefinitionKey());
//     assertThat(resultReportDataDto.getDefinitionVersions())
//         .containsExactly(definition.getVersions().get(0));
//     assertThat(resultReportDataDto.getView()).isNotNull();
//     assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
//
// assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
//
// assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
//     assertThat(resultReportDataDto.getDistributedBy().getType())
//         .isEqualTo(DistributedByType.PROCESS);
//     final VariableGroupByDto variableGroupByDto =
//         (VariableGroupByDto) resultReportDataDto.getGroupBy();
//     assertThat(variableGroupByDto.getValue().getName()).isEqualTo(STRING_VAR);
//     assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);
//
//     assertThat(result.getInstanceCount()).isEqualTo(1);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
//     assertThat(result.getMeasures())
//         .hasSize(1)
//         .extracting(MeasureResponseDto::getProperty, MeasureResponseDto::getData)
//         .hasSize(1)
//         .containsExactly(
//             Tuple.tuple(
//                 ViewProperty.FREQUENCY,
//                 List.of(
//                     createHyperMapResult(
//                         "aStringValue",
//                         new MapResultEntryDto(processIdentifier, 4.0, processDisplayName)))));
//   }
//
//   @Test
//   public void reportEvaluationWithMultipleProcessDefinitionSources() {
//     // given
//     final ProcessInstanceEngineDto firstInstance =
//         engineIntegrationExtension.deployAndStartProcessWithVariables(
//             getTwoServiceTasksProcess("first"),
//             Collections.singletonMap(STRING_VAR, "aStringValue"));
//     final ProcessInstanceEngineDto secondInstance =
//         engineIntegrationExtension.deployAndStartProcessWithVariables(
//             getSingleUserTaskDiagram("second"), Collections.singletonMap(STRING_VAR,
// "aDiffValue"));
//     engineIntegrationExtension.finishAllRunningUserTasks();
//     importAllEngineEntitiesFromScratch();
//
//     final String firstDisplayName = "firstName";
//     final String secondDisplayName = "secondName";
//     final String firstIdentifier = "first";
//     final String secondIdentifier = "second";
//     ReportDataDefinitionDto firstDefinition =
//         new ReportDataDefinitionDto(
//             firstIdentifier, firstInstance.getProcessDefinitionKey(), firstDisplayName);
//     ReportDataDefinitionDto secondDefinition =
//         new ReportDataDefinitionDto(
//             secondIdentifier, secondInstance.getProcessDefinitionKey(), secondDisplayName);
//     final ProcessReportDataDto reportData =
//         createReport(List.of(firstDefinition, secondDefinition), STRING_VAR,
// VariableType.STRING);
//
//     // when
//     final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>>
//         evaluationResponse = reportClient.evaluateHyperMapReport(reportData);
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
//         evaluationResponse.getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(2);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
//     assertThat(result.getMeasures())
//         .hasSize(1)
//         .extracting(MeasureResponseDto::getData)
//         .containsExactly(
//             List.of(
//                 createHyperMapResult(
//                     "aDiffValue",
//                     new MapResultEntryDto(firstIdentifier, null, firstDisplayName),
//                     new MapResultEntryDto(secondIdentifier, 3.0, secondDisplayName)),
//                 createHyperMapResult(
//                     "aStringValue",
//                     new MapResultEntryDto(firstIdentifier, 4.0, firstDisplayName),
//                     new MapResultEntryDto(secondIdentifier, null, secondDisplayName))));
//   }
//
//   @Test
//   public void reportEvaluationWithMultipleProcessDefinitionSourcesAndOverlappingInstances() {
//     // given
//     final ProcessInstanceEngineDto v1instance =
//         engineIntegrationExtension.deployAndStartProcessWithVariables(
//             getTwoServiceTasksProcess("definition"),
//             Collections.singletonMap(STRING_VAR, "aStringValue"));
//     final ProcessInstanceEngineDto v2instance =
//         engineIntegrationExtension.deployAndStartProcessWithVariables(
//             getSingleUserTaskDiagram("definition"),
//             Collections.singletonMap(STRING_VAR, "aStringValue"));
//     engineIntegrationExtension.finishAllRunningUserTasks();
//     importAllEngineEntitiesFromScratch();
//
//     final String v1displayName = "v1";
//     final String allVersionsDisplayName = "all";
//     final String v1Identifier = "v1Identifier";
//     final String allVersionsIdentifier = "allIdentifier";
//     ReportDataDefinitionDto v1definition =
//         new ReportDataDefinitionDto(
//             v1Identifier, v1instance.getProcessDefinitionKey(), v1displayName);
//     v1definition.setVersion("1");
//     ReportDataDefinitionDto allVersionsDefinition =
//         new ReportDataDefinitionDto(
//             allVersionsIdentifier, v2instance.getProcessDefinitionKey(), allVersionsDisplayName);
//     allVersionsDefinition.setVersion(ALL_VERSIONS);
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(List.of(v1definition, allVersionsDefinition), STRING_VAR,
// VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>>
//         evaluationResponse = reportClient.evaluateHyperMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
//         evaluationResponse.getResult();
//     assertThat(result.getInstanceCount()).isEqualTo(2);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
//     assertThat(result.getMeasures())
//         .hasSize(1)
//         .extracting(MeasureResponseDto::getData)
//         .containsExactly(
//             List.of(
//                 createHyperMapResult(
//                     "aStringValue",
//                     new MapResultEntryDto(allVersionsIdentifier, 7.0, allVersionsDisplayName),
//                     new MapResultEntryDto(v1Identifier, 4.0, v1displayName))));
//   }
//
//   private HyperMapResultEntryDto createHyperMapResult(
//       final String flowNodeId, final MapResultEntryDto... results) {
//     return new HyperMapResultEntryDto(flowNodeId, List.of(results), flowNodeId);
//   }
//
//   private ProcessReportDataDto createReport(
//       final List<ReportDataDefinitionDto> definitionDtos,
//       final String variableName,
//       final VariableType variableType) {
//     final ProcessReportDataDto report =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setTenantIds(Collections.singletonList(null))
//             .setVariableName(variableName)
//             .setVariableType(variableType)
//             .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_VARIABLE_BY_PROCESS)
//             .build();
//     report.setDefinitions(definitionDtos);
//     return report;
//   }
// }
