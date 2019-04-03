/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {formatters} from 'services';
import {sortColumns} from './service';

const {convertCamelToSpaces, formatReportResult} = formatters;

export default function processRawData({report: {data, result}}, endpoints = {}) {
  const {
    configuration: {
      excludedColumns = [],
      columnOrder = {instanceProps: [], variables: [], inputVariables: [], outputVariables: []}
    }
  } = data;

  const formattedResult = formatReportResult(data, result);

  const allColumnsLength =
    Object.keys(formattedResult[0]).length - 1 + Object.keys(formattedResult[0].variables).length;
  // If all columns is excluded return a message to enable one
  if (allColumnsLength === excludedColumns.length)
    return {head: ['No Data'], body: [['You need to enable at least one table column']]};

  const instanceProps = Object.keys(formattedResult[0]).filter(
    entry => entry !== 'variables' && !excludedColumns.includes(entry)
  );
  const variableNames = Object.keys(formattedResult[0].variables).filter(
    entry => !excludedColumns.includes('var__' + entry)
  );

  function applyBehavior(type, instance) {
    const content = instance[type];
    if (type === 'processInstanceId') {
      const {endpoint, engineName} = endpoints[instance.engineName] || {};
      if (endpoint) {
        return (
          <a href={`${endpoint}/app/cockpit/${engineName}/#/process-instance/${content}`}>
            {content}
          </a>
        );
      }
    }
    return content;
  }

  const body = formattedResult.map(instance => {
    let row = instanceProps.map(entry => applyBehavior(entry, instance));
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
    head.push({label: 'Variables', columns: variableNames});
  }

  const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

  return {head: sortedHead, body: sortedBody};
}
