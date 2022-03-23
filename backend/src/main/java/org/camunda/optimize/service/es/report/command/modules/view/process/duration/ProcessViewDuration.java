/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import org.apache.commons.math3.util.Precision;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewMultiAggregation;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.DurationScriptUtil.getDurationScript;
import static org.camunda.optimize.service.es.report.command.util.DurationScriptUtil.getUserTaskDurationScript;

public abstract class ProcessViewDuration extends ProcessViewMultiAggregation {

	@Override
	public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
		return ViewProperty.DURATION;
	}

	@Override
	public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
		return getAggregationStrategies(context.getReportData()).stream()
			.map(strategy -> strategy.createAggregationBuilder().script(getScriptedAggregationField(context.getReportData())))
			.collect(Collectors.toList());
	}

	@Override
	public ViewResult retrieveResult(final SearchResponse response,
																	 final Aggregations aggs,
																	 final ExecutionContext<ProcessReportDataDto> context) {
		final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
		getAggregationStrategies(context.getReportData()).forEach(aggregationStrategy -> {
			Double measureResult = aggregationStrategy.getValue(aggs);
			if (measureResult != null) {
				// rounding to closest integer since the lowest precision
				// for duration in the data is milliseconds anyway for data types.
				measureResult = Precision.round(measureResult, 0);
			}
			viewResultBuilder.viewMeasure(
				ViewMeasure.builder().aggregationType(aggregationStrategy.getAggregationType()).value(measureResult).build()
			);
		});
		return viewResultBuilder.build();
	}

	protected abstract String getReferenceDateFieldName(final ProcessReportDataDto reportData);

	protected abstract String getDurationFieldName(final ProcessReportDataDto reportData);

	private Script getScriptedAggregationField(final ProcessReportDataDto reportData) {
		return reportData.isUserTaskReport()
			? getUserTaskDurationScript(
			LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
			getDurationFieldName(reportData)
		)
			: getDurationScript(
			LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
			getDurationFieldName(reportData),
			getReferenceDateFieldName(reportData)
		);
	}
}
