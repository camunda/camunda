/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {reportConfig, formatters, processResult} from 'services';
import {t} from 'translation';

import {sortColumns} from './service';

const {formatReportResult, getRelativeValue, frequency, duration} = formatters;

export default function processDefaultData({report}, processVariables) {
  const {data, result, reportType} = report;
  const {
    configuration: {
      hideAbsoluteValue,
      hideRelativeValue,
      tableColumns: {columnOrder},
    },
    view,
    groupBy,
  } = data;

  const groupedByDuration = groupBy.type === 'duration';
  const instanceCount = result.instanceCount || 0;
  const config = reportConfig[reportType];

  const isMultiMeasure = result.measures.length > 1;

  const viewString = config.view.find(({matcher}) => matcher(data)).label();
  const groupString = config.group.find(({matcher}) => matcher(data)).label();

  const head = [];
  const body = [];

  if (reportType === 'process' && (groupBy.type === 'duration' || groupBy.type.includes('Date'))) {
    head.push(viewString + ' ' + groupString);
  } else if (view.entity === 'processInstance' && groupBy.type === 'variable') {
    head.push(
      `${viewString} ${t('report.table.rawData.variable')}: ${
        getVariableLabel(processVariables, groupBy.value) || groupBy.value.name
      }`
    );
  } else if (['inputVariable', 'outputVariable'].includes(groupBy.type)) {
    head.push(`${t('report.groupBy.' + groupBy.type)}: ${groupBy.value.name}`);
  } else if (view.entity === 'incident' && groupBy.type === 'flowNodes') {
    head.push(t('common.incident.byFlowNode'));
  } else {
    head.push(groupString);
  }

  result.measures.forEach((measure) => {
    const result = processResult({...report, result: measure});
    const formattedResult = formatReportResult(data, result.data);
    if (body.length === 0) {
      formattedResult.forEach(({label, key}) => {
        body.push([groupedByDuration ? duration(label) : label || key]);
      });
    }

    if (measure.property === 'frequency') {
      if (!hideAbsoluteValue) {
        const title = t('report.view.count');
        head.push({label: title, id: title, sortable: !isMultiMeasure});
        formattedResult.forEach(({value}, idx) => {
          body[idx].push(frequency(value));
        });
      }
      if (!hideRelativeValue) {
        const title = t('report.table.relativeFrequency');
        head.push({label: title, id: title, sortable: !isMultiMeasure});
        formattedResult.forEach(({value}, idx) => {
          body[idx].push(getRelativeValue(value, instanceCount));
        });
      }
    } else if (measure.property === 'duration') {
      const title = `${
        measure.userTaskDurationTime
          ? `${t('report.config.userTaskDuration.' + measure.userTaskDurationTime)} `
          : ''
      }${
        view.entity === 'incident' ? t('report.view.resolutionDuration') : t('report.view.duration')
      } - ${t('report.config.aggregationShort.' + measure.aggregationType.type)}`;

      head.push({label: title, id: title, sortable: !isMultiMeasure});
      formattedResult.forEach(({value}, idx) => {
        body[idx].push(duration(value));
      });
    }
  });

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}

function getVariableLabel(variables, {name, type}) {
  return variables.find((variable) => variable.name === name && variable.type === type)?.label;
}
