import React from 'react';

import {Popover, Switch} from 'components';
import {isRawDataReport} from './service';

import './ColumnSelection.css';

export default {
  Content: function ColumnSelection({report, data, change}) {
    if (!isRawDataReport(report, data)) {
      return null;
    }

    const columns = report.result[0];
    const excludedColumns = data.configuration.excludedColumns || [];

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
        {column}
      </div>
    );

    return (
      <div>
        <Popover icon="overflow-menu-vertical" className="ColumnSelection__Popover">
          <div className="ColumnSelection">
            <div className="ColumnSelection__notice">Table columns to include</div>
            {Object.keys(columns)
              .filter(entry => entry !== 'variables')
              .map(renderEntry())}
            {Object.keys(columns.variables).map(renderEntry('var__', 'Variable: '))}
          </div>
        </Popover>
      </div>
    );
  }
};
