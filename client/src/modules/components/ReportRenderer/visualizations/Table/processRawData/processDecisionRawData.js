/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {parseISO} from 'date-fns';

import {format} from 'dates';
import {t} from 'translation';

import {
  sortColumns,
  cockpitLink,
  getNoDataMessage,
  isVisibleColumn,
  getLabelWithType,
} from './service';

export default function processDecisionRawData(
  {
    report: {
      data: {
        configuration: {
          tableColumns,
          columnOrder = {instanceProps: [], variables: [], inputVariables: [], outputVariables: []},
        },
      },
      result: {data: result},
    },
  },
  endpoints = {}
) {
  const instanceProps = Object.keys(result[0]).filter(
    (entry) =>
      entry !== 'inputVariables' &&
      entry !== 'outputVariables' &&
      isVisibleColumn(entry, tableColumns)
  );

  const inputVariables = Object.keys(result[0].inputVariables).filter((entry) =>
    isVisibleColumn('input:' + entry, tableColumns)
  );
  const outputVariables = Object.keys(result[0].outputVariables).filter((entry) =>
    isVisibleColumn('output:' + entry, tableColumns)
  );

  if (instanceProps.length + inputVariables.length + outputVariables.length === 0) {
    return getNoDataMessage();
  }

  const body = result.map((instance) => {
    const propertyValues = instanceProps.map((entry) => {
      if (entry === 'decisionInstanceId') {
        return cockpitLink(endpoints, instance, 'decision');
      }

      if (entry === 'evaluationDateTime' && instance[entry]) {
        return format(parseISO(instance[entry]), "yyyy-MM-dd HH:mm:ss 'UTC'X");
      }

      return instance[entry];
    });
    const inputVariableValues = inputVariables.map((entry) => {
      const value = instance.inputVariables[entry].value;
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    const outputVariableValues = outputVariables.map((entry) => {
      const output = instance.outputVariables[entry];
      if (output && output.values) {
        return output.values.join(', ');
      }
      return '';
    });

    return [...propertyValues, ...inputVariableValues, ...outputVariableValues];
  });

  const head = instanceProps
    .map((key) => {
      const label = t('report.table.rawData.' + key);
      return {id: key, label, title: label};
    })
    .concat(
      inputVariables.map((key) => {
        const {name, id} = result[0].inputVariables[key];
        const label = name || id;
        return {
          type: 'inputVariables',
          id: 'inputVariable:' + id,
          label: getLabelWithType(label, 'inputVariable'),
          title: label,
        };
      })
    )
    .concat(
      outputVariables.map((key) => {
        const {name, id} = result[0].outputVariables[key];
        const label = name || id;
        return {
          type: 'outputVariables',
          id: 'outputVariable:' + id,
          label: getLabelWithType(label, 'outputVariable'),
          title: label,
        };
      })
    );

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
