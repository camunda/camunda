/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidationHelper {
  protected static final Logger logger = LoggerFactory.getLogger(ValidationHelper.class);

  public static void validate(final BranchAnalysisRequestDto dto) {
    ensureNotEmpty("gateway activity id", dto.getGateway());
    ensureNotEmpty("end activity id", dto.getEnd());
    ensureNotEmpty("query dto", dto);
    ensureNotEmpty("ProcessDefinitionKey", dto.getProcessDefinitionKey());
    ensureCollectionNotEmpty("ProcessDefinitionVersion", dto.getProcessDefinitionVersions());
    validateProcessFilters(dto.getFilter());
  }

  public static void ensureNotEmpty(final String fieldName, final Object target) {
    if (target == null || target.toString().isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void ensureCollectionNotEmpty(final String fieldName, final Collection<?> target) {
    if (target == null || target.isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void ensureNotNull(final String fieldName, final Object object) {
    if (object == null) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be null");
    }
  }

  public static void validateCombinedReportDefinition(
      final CombinedReportDefinitionRequestDto combinedReportDefinitionDto,
      final RoleType currentUserRole) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinitionDto =
        new AuthorizedReportDefinitionResponseDto(combinedReportDefinitionDto, currentUserRole);
    if (combinedReportDefinitionDto.getData() == null) {
      final OptimizeValidationException ex =
          new OptimizeValidationException(
              "Report data for a combined report is not allowed to be null!");
      throw new ReportEvaluationException(authorizedReportDefinitionDto, ex);
    } else if (combinedReportDefinitionDto.getData().getReportIds() == null) {
      final OptimizeValidationException ex =
          new OptimizeValidationException(
              "Reports list for a combined report is not allowed to be null!");
      throw new ReportEvaluationException(authorizedReportDefinitionDto, ex);
    }
  }

  private static void validateDefinitionData(final ReportDataDto data) {
    if (data instanceof SingleReportDataDto) {
      final SingleReportDataDto singleReportData = (SingleReportDataDto) data;
      if (data instanceof ProcessReportDataDto
          && !((ProcessReportDataDto) data).isManagementReport()) {
        // it is valid for management reports to not have a key if the user has no authorization for
        // any processes
        ensureNotNull("definitionKey", singleReportData.getDefinitionKey());
      }
      ensureNotNull("definitionVersions", singleReportData.getDefinitionVersions());
    } else if (data == null) {
      throw new OptimizeValidationException("Report data is not allowed to be null!");
    }
  }

  /*
   * A simple boolean wrapper for the .validate() method
   */
  public static boolean isValid(final ReportDataDto dataDto) {
    try {
      validate(dataDto);
      return true;
    } catch (final Exception e) {
      logger.debug("Report Data Validation failed", e);
      return false;
    }
  }

  public static void validate(final ReportDataDto dataDto) {
    validateDefinitionData(dataDto);

    if (dataDto instanceof ProcessReportDataDto) {
      final ProcessReportDataDto processReportDataDto = (ProcessReportDataDto) dataDto;
      ensureNotNull("report data", processReportDataDto);
      final ProcessViewDto viewDto = processReportDataDto.getView();
      ensureNotNull("view", viewDto);
      ensureNotNull("group by", processReportDataDto.getGroupBy());
      validateProcessFilters(processReportDataDto.getFilter());
      validateAggregationTypes(processReportDataDto.getConfiguration().getAggregationTypes());
    } else if (dataDto instanceof DecisionReportDataDto) {
      final DecisionReportDataDto decisionReportDataDto = (DecisionReportDataDto) dataDto;
      ensureNotNull("report data", decisionReportDataDto);
      final DecisionViewDto viewDto = decisionReportDataDto.getView();
      ensureNotNull("view", viewDto);
      ensureNotNull("group by", decisionReportDataDto.getGroupBy());
      validateDecisionFilters(decisionReportDataDto.getFilter());
      validateAggregationTypes(decisionReportDataDto.getConfiguration().getAggregationTypes());
    }
  }

  public static void validateProcessFilters(final List<ProcessFilterDto<?>> filters) {
    if (filters != null) {
      for (final ProcessFilterDto<?> filterDto : filters) {
        if (!filterDto.validApplicationLevels().contains(filterDto.getFilterLevel())) {
          throw new OptimizeValidationException(
              String.format(
                  "%s is not a valid application level for this filter type",
                  filterDto.getFilterLevel()));
        }
        if (filterDto instanceof InstanceStartDateFilterDto) {
          final InstanceStartDateFilterDto instanceStartDateFilterDto =
              (InstanceStartDateFilterDto) filterDto;
          final DateFilterDataDto<?> startDateFilterDataDto = instanceStartDateFilterDto.getData();
          ensureAtLeastOneNotNull(
              "start date filter ",
              startDateFilterDataDto.getStart(),
              startDateFilterDataDto.getEnd());
        } else if (filterDto instanceof VariableFilterDto) {
          final VariableFilterDto variableFilterDto = (VariableFilterDto) filterDto;
          final VariableFilterDataDto<?> variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        } else if (filterDto instanceof ExecutedFlowNodeFilterDto) {
          final ExecutedFlowNodeFilterDto executedFlowNodeFilterDto =
              (ExecutedFlowNodeFilterDto) filterDto;
          final ExecutedFlowNodeFilterDataDto flowNodeFilterData =
              executedFlowNodeFilterDto.getData();
          ensureNotEmpty("operator", flowNodeFilterData.getOperator());
          ensureNotEmpty("values", flowNodeFilterData.getValues());
        } else if (filterDto instanceof ExecutingFlowNodeFilterDto) {
          final ExecutingFlowNodeFilterDto executingFlowNodeFilterDto =
              (ExecutingFlowNodeFilterDto) filterDto;
          final ExecutingFlowNodeFilterDataDto flowNodeFilterData =
              executingFlowNodeFilterDto.getData();
          ensureNotEmpty("values", flowNodeFilterData.getValues());
        } else if (filterDto instanceof CanceledFlowNodeFilterDto) {
          final CanceledFlowNodeFilterDto executingFlowNodeFilterDto =
              (CanceledFlowNodeFilterDto) filterDto;
          final CanceledFlowNodeFilterDataDto flowNodeFilterData =
              executingFlowNodeFilterDto.getData();
          ensureNotEmpty("values", flowNodeFilterData.getValues());
        } else if (filterDto instanceof FlowNodeStartDateFilterDto
            || filterDto instanceof FlowNodeEndDateFilterDto) {
          @SuppressWarnings(UNCHECKED_CAST)
          final ProcessFilterDto<FlowNodeDateFilterDataDto<?>> flowNodeDateFilterDto =
              (ProcessFilterDto<FlowNodeDateFilterDataDto<?>>) filterDto;
          validateFlowNodeDateFilter(flowNodeDateFilterDto);
        }
      }
    }
  }

  public static void validateAggregationTypes(final Set<AggregationDto> aggregationDtos) {
    if (aggregationDtos != null) {
      aggregationDtos.forEach(
          aggType -> {
            final Double aggValue = aggType.getValue();
            if (aggType.getType() == AggregationType.PERCENTILE) {
              if (aggValue == null || aggValue < 0.0 || aggValue > 100.0) {
                throw new OptimizeValidationException(
                    "Percentile aggregation values be between 0 and 100");
              }
            } else if (aggValue != null) {
              throw new OptimizeValidationException(
                  "Aggregation values can only be supplied for percentile " + "aggregations");
            }
          });
    }
  }

  private static void validateFlowNodeDateFilter(
      final ProcessFilterDto<FlowNodeDateFilterDataDto<?>> flowNodeDateFilter) {
    final FlowNodeDateFilterDataDto<?> flowNodeDateFilterDataDto = flowNodeDateFilter.getData();
    if (DateFilterType.FIXED.equals(flowNodeDateFilterDataDto.getType())) {
      ensureAtLeastOneNotNull(
          "flowNode date filter start or end field",
          flowNodeDateFilterDataDto.getStart(),
          flowNodeDateFilterDataDto.getEnd());
    } else {
      ensureNotNull(DateFilterDataDto.Fields.start, flowNodeDateFilterDataDto.getStart());
    }
    if (flowNodeDateFilterDataDto.getStart() instanceof RollingDateFilterStartDto) {
      final RollingDateFilterStartDto rollingStartDto =
          (RollingDateFilterStartDto) flowNodeDateFilterDataDto.getStart();
      ensureNotNull(RollingDateFilterStartDto.Fields.unit, rollingStartDto.getUnit());
      ensureNotNull(RollingDateFilterStartDto.Fields.value, rollingStartDto.getValue());
    } else if (flowNodeDateFilterDataDto.getStart() instanceof RelativeDateFilterStartDto) {
      final RelativeDateFilterStartDto relativeStartDto =
          (RelativeDateFilterStartDto) flowNodeDateFilterDataDto.getStart();
      ensureNotNull(RelativeDateFilterStartDto.Fields.unit, relativeStartDto.getUnit());
      ensureNotNull(RelativeDateFilterStartDto.Fields.value, relativeStartDto.getValue());
    }
    if (FilterApplicationLevel.INSTANCE.equals(flowNodeDateFilter.getFilterLevel())) {
      ensureCollectionNotEmpty(
          FlowNodeDateFilterDataDto.Fields.flowNodeIds, flowNodeDateFilterDataDto.getFlowNodeIds());
    } else {
      ensureNull(
          FlowNodeDateFilterDataDto.Fields.flowNodeIds, flowNodeDateFilterDataDto.getFlowNodeIds());
    }
  }

  private static void validateDecisionFilters(final List<DecisionFilterDto<?>> filters) {
    if (filters != null) {
      for (final DecisionFilterDto<?> filterDto : filters) {
        if (filterDto instanceof EvaluationDateFilterDto) {
          final EvaluationDateFilterDto evaluationDateFilterDto =
              (EvaluationDateFilterDto) filterDto;
          final DateFilterDataDto<?> evaluationDateFilterDataDto =
              evaluationDateFilterDto.getData();

          ensureAtLeastOneNotNull(
              "evaluation date filter ",
              evaluationDateFilterDataDto.getStart(),
              evaluationDateFilterDataDto.getEnd());
        } else if (filterDto instanceof InputVariableFilterDto) {
          final InputVariableFilterDto variableFilterDto = (InputVariableFilterDto) filterDto;
          final VariableFilterDataDto<?> variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        } else if (filterDto instanceof OutputVariableFilterDto) {
          final OutputVariableFilterDto variableFilterDto = (OutputVariableFilterDto) filterDto;
          final VariableFilterDataDto<?> variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        }
      }
    }
  }

  private static void ensureAtLeastOneNotNull(final String fieldName, final Object... objects) {
    final boolean oneNotNull = Arrays.stream(objects).anyMatch(Objects::nonNull);
    if (!oneNotNull) {
      throw new OptimizeValidationException(
          fieldName + " at least one sub field not allowed to be empty or null");
    }
  }

  private static void ensureNull(final String fieldName, final Object object) {
    if (object != null) {
      throw new OptimizeValidationException(fieldName + " has to be null");
    }
  }
}
