import React from 'react';

import {Popover, Switch} from 'components';
import {isRawDataReport} from './service';
import {formatters} from 'services';

import './ColumnSelection.css';

const {convertCamelToSpaces} = formatters;

const VARIABLE_PREFIX = 'var__';

export default {
  Content: function ColumnSelection({report, data, change}) {
    if (!isRawDataReport(report, data)) {
      return null;
    }

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
              change('excludedColumns')(excludedColumns.filter(entry => prefix + column !== entry));
            } else {
              change('excludedColumns')(excludedColumns.concat(prefix + column));
            }
          }}
        />
        <b>{label}</b>
        {prefix === 'var__' ? column : convertCamelToSpaces(column)}
      </div>
    );

    return (
      <div>
        <Popover icon="overflow-menu-vertical" className="ColumnSelection__Popover">
          <div className="ColumnSelection">
            <div className="ColumnSelection__notice">Table columns to include</div>
            <div className="ColumnSelection__entry">
              <Switch
                className="ColumnSelection__Switch"
                checked={excludedColumns.length !== allColumns.length}
                onChange={({target: {checked}}) =>
                  change('excludedColumns')(checked ? [] : [...allColumns])
                }
              />
              All Columns
            </div>
            {normalColumns.map(renderEntry())}
            {variableColumns.map(renderEntry(VARIABLE_PREFIX, 'Variable: '))}
          </div>
        </Popover>
      </div>
    );
  }
};
