/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByUserTask extends ProcessDistributedByPart {

  private static final String USER_TASK_ID_TERMS_AGGREGATION = "tasks";

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return AggregationBuilders
      .terms(USER_TASK_ID_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(USER_TASKS + "." + USER_TASK_ACTIVITY_ID)
      .subAggregation(viewPart.createAggregation(context));
  }

  @Override
  public List<DistributedByResult> retrieveResult(final Aggregations aggregations,
                                                  final ProcessReportDataDto reportData) {

    final Terms byTaskIdAggregation = aggregations.get(USER_TASK_ID_TERMS_AGGREGATION);

    final Map<String, String> userTaskNames = getUserTaskNames(reportData);
    final List<DistributedByResult> distributedByUserTask = new ArrayList<>();
    for (Terms.Bucket taskBucket : byTaskIdAggregation.getBuckets()) {
      final ViewResult viewResult = viewPart.retrieveResult(taskBucket.getAggregations(), reportData);
      final String userTaskKey = taskBucket.getKeyAsString();
      if (userTaskNames.containsKey(userTaskKey)) {
        String label = userTaskNames.get(userTaskKey);
        distributedByUserTask.add(createDistributedByResult(userTaskKey, label, viewResult));
        userTaskNames.remove(userTaskKey);
      }
    }

    // enrich data user tasks that haven't been executed, but should still show up in the result
    userTaskNames.keySet().forEach(userTaskKey -> {
      DistributedByResult emptyResult = DistributedByResult.createResultWithEmptyValue(userTaskKey);
      emptyResult.setLabel(userTaskNames.get(userTaskKey));
      distributedByUserTask.add(emptyResult);
    });

    // by default they should be sorted by label even if no sorting is provided by the user
    return distributedByUserTask.stream()
      .sorted(Comparator.comparing(DistributedByResult::getLabel))
      .collect(Collectors.toList());
  }

  private Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return processDefinitionReader.getProcessDefinitionIfAvailable(reportData)
      .orElse(new ProcessDefinitionOptimizeDto())
      .getUserTaskNames();
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
  }
}
