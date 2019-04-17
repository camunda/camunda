/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters} from 'services';
import {sortColumns, cockpitLink, noData} from './service';

const {convertCamelToSpaces} = formatters;

export default function processDecisionRawData(
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
  const instanceProps = Object.keys(result[0]).filter(
    entry =>
      entry !== 'inputVariables' && entry !== 'outputVariables' && !excludedColumns.includes(entry)
  );

  const inputVariables = Object.keys(result[0].inputVariables).filter(
    entry => !excludedColumns.includes('inp__' + entry)
  );
  const outputVariables = Object.keys(result[0].outputVariables).filter(
    entry => !excludedColumns.includes('out__' + entry)
  );

  if (instanceProps.length + inputVariables.length + outputVariables.length === 0) {
    return noData;
  }

  const body = result.map(instance => {
    const propertyValues = instanceProps.map(entry => {
      if (entry === 'decisionInstanceId') {
        return cockpitLink(endpoints, instance, 'decision');
      }
      return instance[entry];
    });
    const inputVariableValues = inputVariables.map(entry => {
      const value = instance.inputVariables[entry].value;
      if (value === null) {
        return '';
      }
      return value.toString();
    });
    const outputVariableValues = outputVariables.map(entry => {
      const output = instance.outputVariables[entry];
      if (output && output.values) {
        return output.values.join(', ');
      }
      return '';
    });

    return [...propertyValues, ...inputVariableValues, ...outputVariableValues];
  });

  const head = instanceProps.map(convertCamelToSpaces);

  if (inputVariables.length > 0) {
    head.push({
      label: 'Input Variables',
      columns: inputVariables.map(key => {
        const {name, id} = result[0].inputVariables[key];
        return {label: name || id, id: key};
      })
    });
  }
  if (outputVariables.length > 0) {
    head.push({
      label: 'Output Variables',
      columns: outputVariables.map(key => {
        const {name, id} = result[0].outputVariables[key];
        return {label: name || id, id: key};
      })
    });
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
