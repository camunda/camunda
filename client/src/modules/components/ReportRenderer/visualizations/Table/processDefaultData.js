/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {reportConfig, formatters, processResult} from 'services';
import {t} from 'translation';

import {sortColumns} from './service';

const {formatReportResult, getRelativeValue, frequency, duration} = formatters;

export default function processDefaultData({report}) {
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

  const selectedView = config.findSelectedOption(config.options.view, 'data', view);
  const viewString = t('report.view.' + selectedView.key.split('_')[0]);

  const head = [config.getLabelFor('groupBy', config.options.groupBy, groupBy)];
  const body = [];

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
        const title = viewString + ': ' + t('report.view.count');
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
      const title =
        viewString +
        ': ' +
        (view.entity === 'incident'
          ? t('report.view.resolutionDuration')
          : t('report.view.duration'));
      head.push({label: title, id: title, sortable: !isMultiMeasure});
      formattedResult.forEach(({value}, idx) => {
        body[idx].push(duration(value));
      });
    }
  });

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
