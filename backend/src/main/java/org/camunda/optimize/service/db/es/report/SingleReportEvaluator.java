/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report;

import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_LIMIT;
import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_OFFSET;
import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_SCROLL_TIMEOUT;
import static org.camunda.optimize.service.export.CsvExportService.DEFAULT_RECORD_LIMIT;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import org.camunda.optimize.service.db.es.report.command.Command;
import org.camunda.optimize.service.db.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.db.es.report.command.decision.raw.RawDecisionInstanceDataGroupByNoneCmd;
import org.camunda.optimize.service.db.es.report.command.process.processinstance.raw.RawProcessInstanceDataGroupByNoneCmd;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SingleReportEvaluator {

  protected final ConfigurationService configurationService;

  protected final NotSupportedCommand notSupportedCommand;
  protected final ApplicationContext applicationContext;
  protected final Map<String, Command<?, ReportDefinitionDto<?>>> commandSuppliers;

  @Autowired
  @SuppressWarnings(UNCHECKED_CAST)
  public SingleReportEvaluator(
      final ConfigurationService configurationService,
      final NotSupportedCommand notSupportedCommand,
      final ApplicationContext applicationContext,
      final Collection<Command<?, ?>> commands) {
    final boolean isAssigneeAnalyticsEnabled =
        ConfigurationService.getOptimizeProfile(applicationContext.getEnvironment())
                .equals(OptimizeProfile.PLATFORM)
            || configurationService.getUiConfiguration().isUserTaskAssigneeAnalyticsEnabled();
    this.configurationService = configurationService;
    this.notSupportedCommand = notSupportedCommand;
    this.applicationContext = applicationContext;
    commandSuppliers =
        commands.stream()
            .filter(command -> isAssigneeAnalyticsEnabled || !command.isAssigneeReport())
            .collect(
                Collectors.toMap(
                    Command::createCommandKey, c -> applicationContext.getBean(c.getClass())));
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T> SingleReportEvaluationResult<T> evaluate(
      final ReportEvaluationContext<ReportDefinitionDto<?>> reportEvaluationContext)
      throws OptimizeException {
    final List<Command<T, ReportDefinitionDto<?>>> commands =
        extractCommandsWithValidation(reportEvaluationContext);
    final List<CommandEvaluationResult<T>> commandEvaluationResults = new ArrayList<>();
    for (final Command<?, ReportDefinitionDto<?>> command : commands) {
      commandEvaluationResults.add(
          (CommandEvaluationResult<T>) command.evaluate(reportEvaluationContext));
    }
    return new SingleReportEvaluationResult<>(
        reportEvaluationContext.getReportDefinition(), commandEvaluationResults);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T, R extends ReportDefinitionDto<?>> List<Command<T, R>> extractCommands(
      final R reportDefinition) {
    return reportDefinition.getData().createCommandKeys().stream()
        .map(
            commandKey ->
                (Command<T, R>) commandSuppliers.getOrDefault(commandKey, notSupportedCommand))
        .collect(Collectors.toList());
  }

  private <T, R extends ReportDefinitionDto<?>> List<Command<T, R>> extractCommandsWithValidation(
      final ReportEvaluationContext<R> reportEvaluationContext) {
    final R reportDefinition = reportEvaluationContext.getReportDefinition();
    ValidationHelper.validate(reportDefinition.getData());
    final List<Command<T, R>> commands = extractCommands(reportDefinition);
    commands.forEach(command -> validatePaginationValues(reportEvaluationContext, command));
    return commands;
  }

  private <T extends ReportDefinitionDto<?>> void validatePaginationValues(
      final ReportEvaluationContext<T> reportEvaluationContext, final Command<?, T> command) {
    if (isRawDataReport(command)) {
      addDefaultMissingPaginationValues(reportEvaluationContext);
    } else {
      reportEvaluationContext
          .getPagination()
          .ifPresent(
              pagination -> {
                if (pagination.getLimit() != null || pagination.getOffset() != null) {
                  throw new OptimizeValidationException(
                      "Pagination can only be applied to raw data reports");
                }
              });
    }
  }

  private <T extends ReportDefinitionDto<?>> void addDefaultMissingPaginationValues(
      final ReportEvaluationContext<T> reportEvaluationContext) {
    final int offset;
    final int limit;
    final String scrollId;
    final Integer scrollTimeout;
    final PaginationDto completePagination;
    if (reportEvaluationContext.isCsvExport()) {
      offset = 0;
      limit =
          Optional.ofNullable(configurationService.getCsvConfiguration().getExportCsvLimit())
              .orElse(DEFAULT_RECORD_LIMIT);
    } else {
      offset =
          reportEvaluationContext
              .getPagination()
              .filter(pag -> pag.getOffset() != null)
              .map(PaginationDto::getOffset)
              .orElse(PAGINATION_DEFAULT_OFFSET);
      limit =
          reportEvaluationContext
              .getPagination()
              .filter(pag -> pag.getLimit() != null)
              .map(PaginationDto::getLimit)
              .orElse(PAGINATION_DEFAULT_LIMIT);
    }
    final PaginationDto pagData =
        reportEvaluationContext.getPagination().orElse(new PaginationDto());
    if (pagData instanceof PaginationScrollableDto) {
      final PaginationScrollableDto paginationFromRequest = (PaginationScrollableDto) pagData;
      scrollId = paginationFromRequest.getScrollId(); // Could be null, but it's ok
      scrollTimeout =
          Optional.of(paginationFromRequest)
              .filter(pag -> pag.getScrollTimeout() != null)
              .map(PaginationScrollableDto::getScrollTimeout)
              .orElse(PAGINATION_DEFAULT_SCROLL_TIMEOUT);
      completePagination = new PaginationScrollableDto();
      ((PaginationScrollableDto) completePagination).setScrollTimeout(scrollTimeout);
      ((PaginationScrollableDto) completePagination).setScrollId(scrollId);
    } else {
      // Just a normal Pagination Dto or no pagination Dto available
      completePagination = new PaginationDto();
    }
    completePagination.setOffset(offset);
    completePagination.setLimit(limit);
    reportEvaluationContext.setPagination(completePagination);
  }

  private <R extends ReportDefinitionDto<?>> boolean isRawDataReport(final Command<?, R> command) {
    return command instanceof RawDecisionInstanceDataGroupByNoneCmd
        || command instanceof RawProcessInstanceDataGroupByNoneCmd;
  }
}
