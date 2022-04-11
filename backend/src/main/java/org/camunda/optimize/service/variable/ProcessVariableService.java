/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
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
import java.util.Collection;
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

  public List<ProcessVariableNameResponseDto> getVariableNames(final List<ProcessVariableNameRequestDto> variableRequestDtos) {
    return processVariableReader.getVariableNames(variableRequestDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForReports(final List<String> reportIds) {
    final List<ProcessVariableNameRequestDto> processVariableNameRequestDtos = convertReportsToVariableQuery(
      reportService.getAllReportsForIds(reportIds), this::convertToProcessVariableNameRequest
    );
    return processVariableReader.getVariableNames(processVariableNameRequestDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForAuthorizedReports(final String userId,
                                                                                   final List<String> reportIds) {
    final List<ProcessVariableNameRequestDto> processVariableNameRequestDtos = convertAuthorizedReportsToVariableQuery(
      userId, reportIds, this::convertToProcessVariableNameRequest
    );
    return processVariableReader.getVariableNames(processVariableNameRequestDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForReportDefinitions(
    final List<SingleProcessReportDefinitionRequestDto> definitions) {
    final List<ProcessVariableNameRequestDto> processVariableNameRequests = definitions.stream()
      .filter(definition -> definition.getData().getProcessDefinitionKey() != null)
      .map(this::convertToProcessVariableNameRequest)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
    return processVariableReader.getVariableNames(processVariableNameRequests);
  }

  public List<String> getVariableValues(final String userId, final ProcessVariableValueRequestDto requestDto) {
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

  public List<String> getVariableValuesForReports(final String userId,
                                                  final ProcessVariableReportValuesRequestDto requestDto) {
    ensureNotEmpty("report IDs", requestDto.getReportIds());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    final List<ReportDefinitionDto> authorizedReports = getAllAuthorizedReportsRecursively(
      userId, requestDto.getReportIds()
    );
    return processVariableReader.getVariableValues(
      ProcessVariableValuesQueryDto.fromProcessVariableReportValuesRequest(requestDto, authorizedReports)
    );
  }

  private <T> List<T> convertReportsToVariableQuery(final List<ReportDefinitionDto> reportDefinitions,
                                                    final Function<SingleReportDefinitionDto<?>, List<T>> mappingFunction) {
    final List<ReportDefinitionDto> allReportsForIds = new ArrayList<>(reportDefinitions);
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
                                                              final Function<SingleReportDefinitionDto<?>, List<T>> mappingFunction) {
    final List<ReportDefinitionDto> allAuthorizedReportsForIds = getAllAuthorizedReportsRecursively(userId, reportIds);
    return convertReportsToVariableQuery(mappingFunction, allAuthorizedReportsForIds);
  }

  private List<ReportDefinitionDto> getAllAuthorizedReportsRecursively(final String userId,
                                                                       final List<String> reportIds) {
    final List<ReportDefinitionDto> allAuthorizedReportsForIds = reportService.getAllAuthorizedReportsForIds(
      userId, reportIds
    );
    allAuthorizedReportsForIds.addAll(
      allAuthorizedReportsForIds.stream()
        .filter(CombinedReportDefinitionRequestDto.class::isInstance)
        .flatMap(combinedReport -> {
          final List<String> reportIdsFromCombined = ((CombinedReportDefinitionRequestDto) combinedReport).getData()
            .getReportIds();
          return Optional.ofNullable(userId)
            .map(user -> reportService.getAllAuthorizedReportsForIds(userId, reportIdsFromCombined).stream())
            .orElse(reportService.getAllReportsForIds(reportIdsFromCombined).stream());
        }).collect(Collectors.toList()));
    return allAuthorizedReportsForIds;
  }

  private <T> List<T> convertReportsToVariableQuery(final Function<SingleReportDefinitionDto<?>, List<T>> mappingFunction,
                                                    final List<ReportDefinitionDto> reportDefinitionDtos) {
    return reportDefinitionDtos.stream()
      .distinct()
      .filter(SingleReportDefinitionDto.class::isInstance)
      .map(definition -> (SingleReportDefinitionDto<?>) definition)
      .map(mappingFunction)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private List<ProcessVariableNameRequestDto> convertToProcessVariableNameRequest(
    final SingleReportDefinitionDto<?> reportDefinitionDto) {
    return reportDefinitionDto.getData().getDefinitions().stream()
      .map(definitionDto -> {
        final ProcessVariableNameRequestDto variableNameRequest = new ProcessVariableNameRequestDto();
        variableNameRequest.setProcessDefinitionKey(definitionDto.getKey());
        variableNameRequest.setProcessDefinitionVersions(definitionDto.getVersions());
        variableNameRequest.setTenantIds(
          Optional.ofNullable(definitionDto.getTenantIds()).orElse(ReportConstants.DEFAULT_TENANT_IDS)
        );
        return variableNameRequest;
      })
      .collect(Collectors.toList());
  }

}
