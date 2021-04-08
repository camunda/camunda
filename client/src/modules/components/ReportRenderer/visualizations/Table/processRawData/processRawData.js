/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {parseISO} from 'date-fns';

import {format} from 'dates';
import {formatters} from 'services';
import {t} from 'translation';

import {sortColumns} from '../service';
import {cockpitLink, getNoDataMessage, isVisibleColumn, getLabelWithType} from './service';

const {duration} = formatters;

const instanceColumns = [
  'processDefinitionKey',
  'processDefinitionId',
  'processInstanceId',
  'businessKey',
  'startDate',
  'endDate',
  'duration',
  'engineName',
  'tenantId',
];

export default function processRawData(
  {
    report: {
      data: {
        configuration: {tableColumns},
      },
      result: {data: result},
    },
  },
  endpoints = {}
) {
  const instanceProps = instanceColumns.filter((entry) => isVisibleColumn(entry, tableColumns));

  const variableNames = Object.keys(result[0]?.variables || {}).filter((entry) =>
    isVisibleColumn('variable:' + entry, tableColumns)
  );

  // If all columns are excluded return a message to enable one
  if (instanceProps.length + variableNames.length === 0) {
    return getNoDataMessage();
  }

  const body = result.map((instance) => {
    const row = instanceProps.map((entry) => {
      if (entry === 'processInstanceId') {
        return cockpitLink(endpoints, instance, 'process');
      }
      if ((entry === 'startDate' || entry === 'endDate') && instance[entry]) {
        return format(parseISO(instance[entry]), "yyyy-MM-dd HH:mm:ss 'UTC'X");
      }
      if (entry === 'duration') {
        return duration(instance[entry]);
      }
      return instance[entry];
    });
    const variableValues = variableNames.map((entry) => {
      const value = instance.variables[entry];
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    row.push(...variableValues);

    return row;
  });

  const head = instanceProps
    .map((key) => {
      const label = t('report.table.rawData.' + key);
      return {id: key, label, title: label};
    })
    .concat(
      variableNames.map((variable) => ({
        type: 'variables',
        id: 'variable:' + variable,
        label: getLabelWithType(variable, 'variable'),
        title: variable,
      }))
    );

  const {sortedHead, sortedBody} = sortColumns(head, body, tableColumns.columnOrder);

  return {head: sortedHead, body: sortedBody};
}
