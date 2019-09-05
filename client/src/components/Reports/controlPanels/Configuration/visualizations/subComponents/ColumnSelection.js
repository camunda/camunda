/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch} from 'components';
import {formatters} from 'services';
import AllColumnsButtons from './AllColumnsButtons';

import './ColumnSelection.scss';
import {t} from 'translation';

const {convertCamelToSpaces} = formatters;

const labels = {
  var: 'variable',
  inp: 'inputVariable',
  out: 'outputVariable'
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
      return [...prev, ...Object.keys(value).map(key => `${curr.substring(0, 3)}__${key}`)];
    }
  }, []);

  return (
    <fieldset className="ColumnSelection">
      <legend>{t('report.config.includeTableColumn')}</legend>
      <AllColumnsButtons
        enableAll={() => onChange({excludedColumns: {$set: []}})}
        disableAll={() => onChange({excludedColumns: {$set: allColumns}})}
      />
      {allColumns.map(column => {
        let prefix, name;

        if (column.includes('__')) {
          [prefix, name] = column.split('__');
          if (prefix === 'inp') {
            name = columns.inputVariables[name].name;
          } else if (prefix === 'out') {
            name = columns.outputVariables[name].name;
          }
          prefix = t(`common.filter.types.${labels[prefix]}`) + ': ';
        } else {
          prefix = '';
          name = convertCamelToSpaces(column);
        }

        return (
          <Switch
            key={column}
            className="ColumnSelectionSwitch"
            checked={!excludedColumns.includes(column)}
            onChange={({target: {checked}}) => {
              if (checked) {
                onChange({
                  excludedColumns: {$set: excludedColumns.filter(entry => column !== entry)}
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
