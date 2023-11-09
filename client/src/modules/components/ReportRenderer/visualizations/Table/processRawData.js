/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO} from 'date-fns';

import {format} from 'dates';
import {formatters} from 'services';
import {t} from 'translation';
import {Button} from 'components';

import {
  cockpitLink,
  getNoDataMessage,
  isVisibleColumn,
  getLabelWithType,
  sortColumns,
} from './service';

const {duration} = formatters;

const instanceColumns = {
  process: [
    'processDefinitionKey',
    'processDefinitionId',
    'processInstanceId',
    'businessKey',
    'startDate',
    'endDate',
    'duration',
    'engineName',
    'tenantId',
  ],
  decision: [
    'decisionDefinitionKey',
    'decisionDefinitionId',
    'decisionInstanceId',
    'processInstanceId',
    'evaluationDateTime',
    'engineName',
    'tenantId',
  ],
};

export const OBJECT_VARIABLE_IDENTIFIER = '<<OBJECT_VARIABLE_VALUE>>';

export default function processRawData({
  report: {
    reportType,
    data: {
      configuration: {tableColumns},
    },
    result: {data: result},
  },
  camundaEndpoints = {},
  processVariables = [],
  onVariableView,
}) {
  const instanceProps = instanceColumns[reportType].filter((entry) =>
    isVisibleColumn(entry, tableColumns)
  );

  const {
    counts,
    variables,
    flowNodeDurations,
    inputVariables: inputVars,
    outputVariables: outputVars,
  } = result[0] || {};

  const countKeys = getVisibleColumns(counts, tableColumns, 'count');

  const variableNames = getVisibleColumns(variables, tableColumns, 'variable');

  const inputVariables = getVisibleColumns(inputVars, tableColumns, 'input');

  const outputVariables = getVisibleColumns(outputVars, tableColumns, 'output');

  const flowNodeDurationNames = getVisibleColumns(
    flowNodeDurations || {},
    tableColumns,
    'flowNodeDuration'
  );

  // If all columns are excluded return a message to enable one
  if (
    instanceProps.length +
      countKeys.length +
      variableNames.length +
      inputVariables.length +
      outputVariables.length +
      flowNodeDurationNames.length ===
    0
  ) {
    return getNoDataMessage();
  }

  const body = result.map((instance) => {
    const row = instanceProps.map((entry) => {
      if (entry === 'processInstanceId') {
        return cockpitLink(camundaEndpoints, instance, 'process');
      }

      if (entry === 'decisionInstanceId') {
        return cockpitLink(camundaEndpoints, instance, 'decision');
      }

      if (
        (entry === 'startDate' || entry === 'endDate' || entry === 'evaluationDateTime') &&
        instance[entry]
      ) {
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
      ...getVariableValues(inputVariables, instance.inputVariables),
      ...getVariableValues(outputVariables, instance.outputVariables),
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
      inputVariables.map((key) => {
        const {name, id} = inputVars[key];
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
        const {name, id} = outputVars[key];
        const label = name || id;
        return {
          type: 'outputVariables',
          id: 'outputVariable:' + id,
          label: getLabelWithType(label, 'outputVariable'),
          title: label,
        };
      })
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
          link
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
