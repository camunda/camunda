/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.variable;

import static io.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.reader.ProcessVariableReader;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.util.tenant.DataSourceTenantAuthorizationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessVariableService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ProcessVariableService.class);
  private final ProcessVariableReader processVariableReader;
  private final DataSourceTenantAuthorizationService tenantAuthorizationService;
  private final ReportService reportService;

  public ProcessVariableService(
      final ProcessVariableReader processVariableReader,
      final DataSourceTenantAuthorizationService tenantAuthorizationService,
      final ReportService reportService) {
    this.processVariableReader = processVariableReader;
    this.tenantAuthorizationService = tenantAuthorizationService;
    this.reportService = reportService;
  }

  public List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableRequestDto) {
    return processVariableReader.getVariableNames(variableRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForReports(
      final List<String> reportIds) {
    final List<ProcessToQueryDto> processesToQuery =
        convertReportsToVariableQuery(
            reportService.getAllReportsForIds(reportIds), this::convertToProcessToQueryDto);

    final ProcessVariableNameRequestDto processVariableNameRequestDto =
        new ProcessVariableNameRequestDto(processesToQuery);
    return processVariableReader.getVariableNames(processVariableNameRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForAuthorizedReports(
      final String userId, final List<String> reportIds) {
    final List<ProcessToQueryDto> processesToQuery =
        convertAuthorizedReportsToVariableQuery(
            userId, reportIds, this::convertToProcessToQueryDto);

    final ProcessVariableNameRequestDto processVariableNameRequestDto =
        new ProcessVariableNameRequestDto(processesToQuery);
    return processVariableReader.getVariableNames(processVariableNameRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForReportDefinitions(
      final List<SingleProcessReportDefinitionRequestDto> definitions) {
    final List<ProcessToQueryDto> processesToQuery =
        definitions.stream()
            .filter(definition -> definition.getData().getProcessDefinitionKey() != null)
            .map(this::convertToProcessToQueryDto)
            .flatMap(Collection::stream)
            .toList();

    final ProcessVariableNameRequestDto processVariableNameRequestDto =
        new ProcessVariableNameRequestDto(processesToQuery);
    return processVariableReader.getVariableNames(processVariableNameRequestDto);
  }

  public List<String> getVariableValues(
      final String userId, final ProcessVariableValueRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(
        userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException(
          "Current user is not authorized to access data of all provided tenants");
    }
    if (requestDto.getProcessDefinitionVersions().isEmpty()) {
      return Collections.emptyList();
    } else {
      return processVariableReader.getVariableValues(
          ProcessVariableValuesQueryDto.fromProcessVariableValueRequestDto(requestDto));
    }
  }

  public List<String> getVariableValuesForReports(
      final String userId, final ProcessVariableReportValuesRequestDto requestDto) {
    ensureNotEmpty("report IDs", requestDto.getReportIds());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    final List<ReportDefinitionDto> authorizedReports =
        getAllAuthorizedReportsRecursively(userId, requestDto.getReportIds());
    return processVariableReader.getVariableValues(
        ProcessVariableValuesQueryDto.fromProcessVariableReportValuesRequest(
            requestDto, authorizedReports));
  }

  private <T> List<T> convertReportsToVariableQuery(
      final List<ReportDefinitionDto> reportDefinitions,
      final Function<SingleReportDefinitionDto<?>, List<T>> mappingFunction) {
    final List<ReportDefinitionDto> allReportsForIds = new ArrayList<>(reportDefinitions);
    allReportsForIds.addAll(
        allReportsForIds.stream()
            .filter(
                reportDefinitionDto ->
                    reportDefinitionDto instanceof CombinedReportDefinitionRequestDto)
            .flatMap(
                combinedReport -> {
                  final List<String> reportIdsFromCombined =
                      ((CombinedReportDefinitionRequestDto) combinedReport)
                          .getData()
                          .getReportIds();
                  return reportService.getAllReportsForIds(reportIdsFromCombined).stream();
                })
            .collect(Collectors.toList()));

    return convertReportsToVariableQuery(mappingFunction, allReportsForIds);
  }

  private <T> List<T> convertAuthorizedReportsToVariableQuery(
      final String userId,
      final List<String> reportIds,
      final Function<SingleReportDefinitionDto<?>, List<T>> mappingFunction) {
    final List<ReportDefinitionDto> allAuthorizedReportsForIds =
        getAllAuthorizedReportsRecursively(userId, reportIds);
    return convertReportsToVariableQuery(mappingFunction, allAuthorizedReportsForIds);
  }

  private List<ReportDefinitionDto> getAllAuthorizedReportsRecursively(
      final String userId, final List<String> reportIds) {
    final List<ReportDefinitionDto> allAuthorizedReportsForIds =
        reportService.getAllAuthorizedReportsForIds(userId, reportIds);
    allAuthorizedReportsForIds.addAll(
        allAuthorizedReportsForIds.stream()
            .filter(CombinedReportDefinitionRequestDto.class::isInstance)
            .flatMap(
                combinedReport -> {
                  final List<String> reportIdsFromCombined =
                      ((CombinedReportDefinitionRequestDto) combinedReport)
                          .getData()
                          .getReportIds();
                  return Optional.ofNullable(userId)
                      .map(
                          user ->
                              reportService
                                  .getAllAuthorizedReportsForIds(userId, reportIdsFromCombined)
                                  .stream())
                      .orElse(reportService.getAllReportsForIds(reportIdsFromCombined).stream());
                })
            .collect(Collectors.toList()));
    return allAuthorizedReportsForIds;
  }

  private <T> List<T> convertReportsToVariableQuery(
      final Function<SingleReportDefinitionDto<?>, List<T>> mappingFunction,
      final List<ReportDefinitionDto> reportDefinitionDtos) {
    return reportDefinitionDtos.stream()
        .distinct()
        .filter(SingleReportDefinitionDto.class::isInstance)
        .map(definition -> (SingleReportDefinitionDto<?>) definition)
        .map(mappingFunction)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private List<ProcessToQueryDto> convertToProcessToQueryDto(
      final SingleReportDefinitionDto<?> reportDefinitionDto) {
    return reportDefinitionDto.getData().getDefinitions().stream()
        .map(
            definitionDto -> {
              final ProcessToQueryDto processToQuery = new ProcessToQueryDto();
              processToQuery.setProcessDefinitionKey(definitionDto.getKey());
              processToQuery.setProcessDefinitionVersions(definitionDto.getVersions());
              processToQuery.setTenantIds(
                  Optional.ofNullable(definitionDto.getTenantIds())
                      .orElse(ReportConstants.DEFAULT_TENANT_IDS));
              return processToQuery;
            })
        .collect(Collectors.toList());
  }
}
