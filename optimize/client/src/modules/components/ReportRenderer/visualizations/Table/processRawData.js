/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseISO} from 'date-fns';
import {Button} from '@carbon/react';

import {format} from 'dates';
import {formatters} from 'services';
import {t} from 'translation';

import {
  getNoDataMessage,
  isVisibleColumn,
  getLabelWithType,
  sortColumns,
} from './service';

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

export const OBJECT_VARIABLE_IDENTIFIER = '<<OBJECT_VARIABLE_VALUE>>';

export default function processRawData({
  report: {
    data: {
      configuration: {tableColumns},
    },
    result: {data: result},
  },
  processVariables = [],
  onVariableView,
}) {
  const instanceProps = instanceColumns.filter((entry) => isVisibleColumn(entry, tableColumns));

  const {counts, variables, flowNodeDurations} = result[0] || {};

  const countKeys = getVisibleColumns(counts, tableColumns, 'count');

  const variableNames = getVisibleColumns(variables, tableColumns, 'variable');

  const flowNodeDurationNames = getVisibleColumns(flowNodeDurations || {}, tableColumns, 'dur');

  // If all columns are excluded return a message to enable one
  if (
    instanceProps.length +
      countKeys.length +
      variableNames.length +
      flowNodeDurationNames.length ===
    0
  ) {
    return getNoDataMessage();
  }

  const body = result.map((instance) => {
    const row = instanceProps.map((entry) => {
      if ((entry === 'startDate' || entry === 'endDate') && instance[entry]) {
        return format(parseISO(instance[entry]), "yyyy-MM-dd HH:mm:ss 'UTC'X");
      }

      if (entry === 'duration') {
        return duration(instance[entry]);
      }

      return instance[entry];
    });

    const onVariableClick = (variableName) =>
      onVariableView(variableName, instance.processInstanceId, instance.processDefinitionKey);

    return [
      ...row,
      ...getVariableValues(countKeys, instance.counts),
      ...getVariableValues(variableNames, instance.variables, onVariableClick),
      ...flowNodeDurationNames.map((key) => duration(instance.flowNodeDurations[key]?.value)),
    ];
  });

  const head = instanceProps
    .map((key) => {
      const label = t('report.table.rawData.' + key);
      return {id: key, label, title: label};
    })
    .concat(
      countKeys.map((key) => {
        const name = t('report.table.rawData.' + key);
        return {
          type: 'counts',
          // we dont give them any prefix to keep compatibility with included/excluded columns
          id: key,
          label: getLabelWithType(name, 'count'),
          title: name,
        };
      })
    )
    .concat(
      variableNames.map((name) => ({
        type: 'variables',
        id: 'variable:' + name,
        label: getLabelWithType(getVariableLabel(processVariables, name) || name, 'variable'),
        title: name,
      }))
    )
    .concat(
      flowNodeDurationNames.map((key) => {
        const {name} = flowNodeDurations[key];
        const label = name || key;
        return {
          type: 'flowNodeDurations',
          id: 'flowNodeDuration:' + key,
          label: getLabelWithType(label, 'flowNodeDuration'),
          title: label,
          sortable: false,
        };
      })
    );
  const {sortedHead, sortedBody} = sortColumns(head, body, tableColumns.columnOrder);

  return {head: sortedHead, body: sortedBody};
}

function getVariableValues(variableKeys, variableValues, onVariableClick) {
  return variableKeys.map((entry) => {
    const variableData = variableValues[entry];

    if (variableData === OBJECT_VARIABLE_IDENTIFIER) {
      return (
        <Button
          className="ObjectViewBtn"
          kind="ghost"
          onClick={() => {
            onVariableClick(entry);
          }}
        >
          {t('common.view')}
        </Button>
      );
    }

    // Output variables have multiple values
    if (variableData?.values) {
      return variableData.values.join(', ');
    }

    if (variableData?.value) {
      return variableData.value.toString();
    }

    if (variableData !== undefined && typeof variableData !== 'object') {
      return variableData.toString();
    }

    return '';
  });
}

function getVariableLabel(variables, name) {
  return variables.find((variable) => variable.name === name)?.label;
}

function getVisibleColumns(column = {}, tableColumns, type) {
  return Object.keys(column).filter((entry) => isVisibleColumn(`${type}:${entry}`, tableColumns));
}
