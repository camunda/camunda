/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {formatters} from 'services';
import {sortColumns} from './service';

const {convertCamelToSpaces, formatReportResult} = formatters;

export default function processDecisionRawData({report: {data, result}}, endpoints = {}) {
  const {
    configuration: {
      excludedColumns = [],
      columnOrder = {instanceProps: [], variables: [], inputVariables: [], outputVariables: []}
    }
  } = data;

  const formattedResult = formatReportResult(data, result);

  const instanceProps = Object.keys(formattedResult[0]).filter(
    entry =>
      entry !== 'inputVariables' && entry !== 'outputVariables' && !excludedColumns.includes(entry)
  );

  const inputVariables = Object.keys(formattedResult[0].inputVariables).filter(
    entry => !excludedColumns.includes('inp__' + entry)
  );
  const outputVariables = Object.keys(formattedResult[0].outputVariables).filter(
    entry => !excludedColumns.includes('out__' + entry)
  );

  if (instanceProps.length + inputVariables.length + outputVariables.length === 0) {
    return {head: ['No Data'], body: [['You need to enable at least one table column']]};
  }

  function applyBehavior(type, instance) {
    const content = instance[type];
    if (type === 'decisionInstanceId') {
      const {endpoint, engineName} = endpoints[instance.engineName] || {};
      if (endpoint) {
        return (
          <a href={`${endpoint}/app/cockpit/${engineName}/#/decision-instance/${content}`}>
            {content}
          </a>
        );
      }
    }
    return content;
  }

  const body = formattedResult.map(instance => {
    const propertyValues = instanceProps.map(entry => applyBehavior(entry, instance));
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
        const {name, id} = formattedResult[0].inputVariables[key];
        return {label: name || id, id: key};
      })
    });
  }
  if (outputVariables.length > 0) {
    head.push({
      label: 'Output Variables',
      columns: outputVariables.map(key => {
        const {name, id} = formattedResult[0].outputVariables[key];
        return {label: name || id, id: key};
      })
    });
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
