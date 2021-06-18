/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import org.camunda.optimize.service.es.reader.ProcessVariableReader;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.util.tenant.DataSourceTenantAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessVariableService {

  private final ProcessVariableReader processVariableReader;
  private final DataSourceTenantAuthorizationService tenantAuthorizationService;
  private final ReportService reportService;

  public List<ProcessVariableNameResponseDto> getVariableNames(List<ProcessVariableNameRequestDto> variableRequestDtos) {
    return processVariableReader.getVariableNames(variableRequestDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForReports(List<String> reportIds) {
    final List<ProcessVariableNameRequestDto> processVariableNameRequestDtos = convertReportsToVariableQuery(
      reportIds,
      definitionDto -> convertToProcessVariableNameRequest((SingleProcessReportDefinitionRequestDto) definitionDto)
    );
    return processVariableReader.getVariableNames(processVariableNameRequestDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForAuthorizedReports(String userId,
                                                                                   List<String> reportIds) {
    final List<ProcessVariableNameRequestDto> processVariableNameRequestDtos = convertAuthorizedReportsToVariableQuery(
      userId,
      reportIds,
      definitionDto -> convertToProcessVariableNameRequest((SingleProcessReportDefinitionRequestDto) definitionDto)
    );
    return processVariableReader.getVariableNames(processVariableNameRequestDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForReportDefinitions(List<SingleProcessReportDefinitionRequestDto> definitions) {
    final List<ProcessVariableNameRequestDto> processVariableNameRequests = definitions.stream()
      .filter(definition -> definition.getData().getProcessDefinitionKey() != null)
      .map(this::convertToProcessVariableNameRequest)
      .collect(Collectors.toList());
    return processVariableReader.getVariableNames(processVariableNameRequests);
  }

  public List<String> getVariableValues(String userId, ProcessVariableValueRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    if (requestDto.getProcessDefinitionVersions().isEmpty()) {
      return Collections.emptyList();
    } else {
      return processVariableReader.getVariableValues(
        ProcessVariableValuesQueryDto.fromProcessVariableValueRequestDto(requestDto));
    }
  }

  public List<String> getVariableValuesForReports(String userId, ProcessVariableReportValuesRequestDto requestDto) {
    ensureNotEmpty("report IDs", requestDto.getReportIds());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    final List<SingleProcessReportDefinitionRequestDto> authorizedReports = convertAuthorizedReportsToVariableQuery(
      userId,
      requestDto.getReportIds(),
      SingleProcessReportDefinitionRequestDto.class::cast
    );
    return processVariableReader.getVariableValues(
      ProcessVariableValuesQueryDto.fromProcessVariableReportValuesRequest(requestDto, authorizedReports)
    );
  }

  private <T> List<T> convertReportsToVariableQuery(final List<String> reportIds,
                                                    final Function<ReportDefinitionDto, T> mappingFunction) {
    final List<ReportDefinitionDto> allReportsForIds = reportService.getAllReportsForIds(reportIds);
    allReportsForIds.addAll(
      allReportsForIds.stream()
        .filter(reportDefinitionDto -> reportDefinitionDto instanceof CombinedReportDefinitionRequestDto)
        .flatMap(combinedReport -> {
          final List<String> reportIdsFromCombined =
            ((CombinedReportDefinitionRequestDto) combinedReport).getData().getReportIds();
          return reportService.getAllReportsForIds(reportIdsFromCombined).stream();
        })
        .collect(Collectors.toList()));

    return convertReportsToVariableQuery(mappingFunction, allReportsForIds);
  }

  private <T> List<T> convertAuthorizedReportsToVariableQuery(final String userId,
                                                              final List<String> reportIds,
                                                              final Function<ReportDefinitionDto, T> mappingFunction) {
    final List<ReportDefinitionDto> allAuthorizedReportsForIds = reportService.getAllAuthorizedReportsForIds(
      userId,
      reportIds
    );
    allAuthorizedReportsForIds.addAll(
      allAuthorizedReportsForIds.stream()
        .filter(reportDefinitionDto -> reportDefinitionDto instanceof CombinedReportDefinitionRequestDto)
        .flatMap(combinedReport -> {
          final List<String> reportIdsFromCombined = ((CombinedReportDefinitionRequestDto) combinedReport).getData()
            .getReportIds();
          return reportService.getAllAuthorizedReportsForIds(userId, reportIdsFromCombined).stream();
        }).collect(Collectors.toList()));
    return convertReportsToVariableQuery(mappingFunction, allAuthorizedReportsForIds);
  }

  private <T> List<T> convertReportsToVariableQuery(final Function<ReportDefinitionDto, T> mappingFunction,
                                                    final List<ReportDefinitionDto> reportDefinitionDtos) {
    return reportDefinitionDtos
      .stream()
      .distinct()
      .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
      .map(mappingFunction)
      .collect(Collectors.toList());
  }

  private ProcessVariableNameRequestDto convertToProcessVariableNameRequest(final SingleProcessReportDefinitionRequestDto reportDefinitionDto) {
    final ProcessVariableNameRequestDto variableNameRequest = new ProcessVariableNameRequestDto();
    variableNameRequest.setProcessDefinitionKey(reportDefinitionDto.getData().getDefinitionKey());
    variableNameRequest.setProcessDefinitionVersions(reportDefinitionDto.getData().getProcessDefinitionVersions());
    variableNameRequest.setTenantIds(Optional.ofNullable(reportDefinitionDto.getData().getTenantIds())
                                       .orElse(new ArrayList<>(Collections.singletonList(null))));
    return variableNameRequest;
  }

}
