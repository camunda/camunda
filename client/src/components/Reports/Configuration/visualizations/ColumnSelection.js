import React from 'react';

import {Switch} from 'components';
import {formatters} from 'services';

import './ColumnSelection.scss';

const {convertCamelToSpaces} = formatters;

const VARIABLE_PREFIX = 'var__';

export default function ColumnSelection({report, onChange}) {
  const {data} = report;
  const columns = report.result[0];
  const excludedColumns = data.configuration.excludedColumns || [];
  const normalColumns = Object.keys(columns).filter(entry => entry !== 'variables');
  const variableColumns = Object.keys(columns.variables);
  const allColumns = [
    ...normalColumns,
    ...variableColumns.map(variable => VARIABLE_PREFIX + variable)
  ];

  const renderEntry = (prefix = '', label = '') => column => (
    <div key={column} className="ColumnSelection__entry">
      <Switch
        className="ColumnSelection__Switch"
        checked={!excludedColumns.includes(prefix + column)}
        onChange={({target: {checked}}) => {
          if (checked) {
            onChange('excludedColumns', excludedColumns.filter(entry => prefix + column !== entry));
          } else {
            onChange('excludedColumns', excludedColumns.concat(prefix + column));
          }
        }}
      />
      <b>{label}</b>
      {prefix === 'var__' ? column : convertCamelToSpaces(column)}
    </div>
  );

  return (
    <div className="ColumnSelection">
      <div className="ColumnSelection__notice">Table columns to include</div>
      <div className="ColumnSelection__entry">
        <Switch
          className="ColumnSelection__Switch"
          checked={excludedColumns.length !== allColumns.length}
          onChange={({target: {checked}}) =>
            onChange('excludedColumns', checked ? [] : [...allColumns])
          }
        />
        All Columns
      </div>
      {normalColumns.map(renderEntry())}
      {variableColumns.map(renderEntry(VARIABLE_PREFIX, 'Variable: '))}
    </div>
  );
}
