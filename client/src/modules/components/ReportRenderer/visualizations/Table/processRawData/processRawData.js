/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import moment from 'moment';

import {formatters} from 'services';
import {sortColumns, cockpitLink, getNoDataMessage} from './service';
import {t} from 'translation';

const {convertCamelToSpaces} = formatters;

export default function processRawData(
  {
    report: {
      data: {
        configuration: {
          excludedColumns = [],
          columnOrder = {instanceProps: [], variables: [], inputVariables: [], outputVariables: []}
        }
      },
      result: {data: result}
    }
  },
  endpoints = {}
) {
  const allColumnsLength =
    Object.keys(result[0]).length - 1 + Object.keys(result[0].variables).length;
  // If all columns is excluded return a message to enable one
  if (allColumnsLength === excludedColumns.length) {
    return getNoDataMessage();
  }

  const instanceProps = Object.keys(result[0]).filter(
    entry => entry !== 'variables' && !excludedColumns.includes(entry)
  );
  const variableNames = Object.keys(result[0].variables).filter(
    entry => !excludedColumns.includes('var__' + entry)
  );

  const body = result.map(instance => {
    const row = instanceProps.map(entry => {
      if (entry === 'processInstanceId') {
        return cockpitLink(endpoints, instance, 'process');
      }
      if ((entry === 'startDate' || entry === 'endDate') && instance[entry]) {
        return moment.parseZone(instance[entry]).format('YYYY-MM-DD HH:mm:ss [UTC]Z');
      }
      return instance[entry];
    });
    const variableValues = variableNames.map(entry => {
      const value = instance.variables[entry];
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    row.push(...variableValues);

    return row;
  });

  const head = instanceProps.map(convertCamelToSpaces);

  if (variableNames.length > 0) {
    head.push({label: t('report.variables.default'), columns: variableNames});
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
