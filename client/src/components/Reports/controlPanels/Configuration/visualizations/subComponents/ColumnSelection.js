/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch} from 'components';
import AllColumnsButtons from './AllColumnsButtons';

import './ColumnSelection.scss';
import {t} from 'translation';

const labels = {
  inputVariables: 'input',
  outputVariables: 'output',
  variables: 'variable',
};

export default function ColumnSelection({report, onChange}) {
  const {data} = report;
  const columns = report.result.data[0];

  if (!columns) {
    return null;
  }

  const excludedColumns = data.configuration.excludedColumns || [];
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
        enableAll={() => onChange({excludedColumns: {$set: []}})}
        disableAll={() => onChange({excludedColumns: {$set: allColumns}})}
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
                  excludedColumns: {$set: excludedColumns.filter((entry) => column !== entry)},
                });
              } else {
                onChange({excludedColumns: {$set: excludedColumns.concat(column)}});
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
