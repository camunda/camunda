/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch, LabeledInput} from 'components';
import {t} from 'translation';
import {getReportResult} from 'services';
import {getVariableLabel} from 'variables';

import AllColumnsButtons from './AllColumnsButtons';

import './ColumnSelection.scss';

const labels = {
  inputVariables: 'input',
  outputVariables: 'output',
  variables: 'variable',
};

export default function ColumnSelection({report, onChange}) {
  const {data} = report;
  const columns = getReportResult(report)?.data[0];

  if (!columns) {
    return null;
  }

  const {
    tableColumns: {excludedColumns, includedColumns, includeNewVariables},
  } = data.configuration;

  const allColumns = Object.keys(columns).reduce((prev, curr) => {
    const value = columns[curr];
    if (typeof value !== 'object' || value === null) {
      return [...prev, curr];
    } else {
      return [...prev, ...Object.keys(value).map((key) => `${labels[curr]}:${key}`)];
    }
  }, []);

  return (
    <fieldset className="ColumnSelection">
      <legend>{t('report.config.includeTableColumn')}</legend>
      <AllColumnsButtons
        enableAll={() =>
          onChange({
            tableColumns: {excludedColumns: {$set: []}, includedColumns: {$set: allColumns}},
          })
        }
        disableAll={() =>
          onChange({
            tableColumns: {excludedColumns: {$set: allColumns}, includedColumns: {$set: []}},
          })
        }
      />
      <LabeledInput
        className="includeNew"
        type="checkbox"
        checked={includeNewVariables}
        label={t('report.config.includeNewVariables')}
        onChange={({target: {checked}}) =>
          onChange({tableColumns: {includeNewVariables: {$set: checked}}})
        }
      />
      {allColumns.map((column) => {
        let prefix, name;

        if (column.includes(':')) {
          [prefix, name] = column.split(':');
          let type = 'variable';
          if (prefix === 'input') {
            name = columns.inputVariables[name].name;
            type = 'inputVariable';
          } else if (prefix === 'output') {
            name = columns.outputVariables[name].name;
            type = 'outputVariable';
          } else {
            name = getVariableLabel(name);
          }
          prefix = t(`common.filter.types.${type}`) + ': ';
        } else {
          prefix = '';
          name = t('report.table.rawData.' + column);
        }

        return (
          <Switch
            key={column}
            className="columnSelectionSwitch"
            checked={!excludedColumns.includes(column)}
            onChange={({target: {checked}}) => {
              if (checked) {
                onChange({
                  tableColumns: {
                    excludedColumns: {$set: excludedColumns.filter((entry) => column !== entry)},
                    includedColumns: {$push: [column]},
                  },
                });
              } else {
                onChange({
                  tableColumns: {
                    excludedColumns: {$push: [column]},
                    includedColumns: {$set: includedColumns.filter((entry) => column !== entry)},
                  },
                });
              }
            }}
            label={
              <>
                <b>{prefix}</b>
                {name}
              </>
            }
          />
        );
      })}
    </fieldset>
  );
}
