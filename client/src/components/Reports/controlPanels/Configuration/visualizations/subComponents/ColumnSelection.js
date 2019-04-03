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

const {convertCamelToSpaces} = formatters;

const labels = {
  var: 'Variable: ',
  inp: 'Input Variable: ',
  out: 'Output Variable: '
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
  const excludedColumnsCount = excludedColumns.length;

  return (
    <fieldset className="ColumnSelection">
      <legend>Table columns to include</legend>
      <AllColumnsButtons
        allEnabled={!excludedColumnsCount}
        allDisabled={excludedColumnsCount === allColumns.length}
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
          prefix = labels[prefix];
        } else {
          prefix = '';
          name = convertCamelToSpaces(column);
        }

        return (
          <div key={column} className="ColumnSelection__entry">
            <Switch
              className="ColumnSelection__Switch"
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
            />
            <b>{prefix}</b>
            {name}
          </div>
        );
      })}
    </fieldset>
  );
}
