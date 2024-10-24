/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_LIMIT;
import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_OFFSET;
import static io.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_SCROLL_TIMEOUT;
import static io.camunda.optimize.service.export.CsvExportService.DEFAULT_RECORD_LIMIT;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.service.db.report.interpreter.plan.ExecutionPlanInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.util.ValidationHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class SingleReportEvaluator {

  private final ConfigurationService configurationService;
  private final ExecutionPlanExtractor executionPlanExtractor;
  private final ExecutionPlanInterpreterFacade interpreter;

  public SingleReportEvaluator(
      final ConfigurationService configurationService,
      final ExecutionPlanExtractor executionPlanExtractor,
      final ExecutionPlanInterpreterFacade interpreter) {
    this.configurationService = configurationService;
    this.executionPlanExtractor = executionPlanExtractor;
    this.interpreter = interpreter;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <D extends SingleReportDataDto> SingleReportEvaluationResult<Object> evaluate(
      final ReportEvaluationContext<? extends SingleReportDefinitionDto<D>> reportEvaluationContext)
      throws OptimizeException {
    final List<CommandEvaluationResult<Object>> results =
        extractExecutionPlansWithValidation(reportEvaluationContext)
            .map(
                plan ->
                    ExecutionContextFactory.buildExecutionContext(plan, reportEvaluationContext))
            .map(interpreter::interpret)
            .toList();
    return new SingleReportEvaluationResult<>(
        reportEvaluationContext.getReportDefinition(), results);
  }

  private <R extends ReportDefinitionDto<?>>
      Stream<ExecutionPlan> extractExecutionPlansWithValidation(
          final ReportEvaluationContext<R> reportEvaluationContext) {
    final R reportDefinition = reportEvaluationContext.getReportDefinition();
    ValidationHelper.validate(reportDefinition.getData());
    return executionPlanExtractor.extractExecutionPlans(reportDefinition).stream()
        .peek(executionPlan -> validatePaginationValues(reportEvaluationContext, executionPlan));
  }

  private <T extends ReportDefinitionDto<?>> void validatePaginationValues(
      final ReportEvaluationContext<T> reportEvaluationContext, final ExecutionPlan executionPlan) {
    if (executionPlan.isRawDataReport()) {
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
    if (pagData instanceof final PaginationScrollableDto paginationFromRequest) {
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
}
