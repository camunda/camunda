import React from 'react';

import {Table as TableRenderer} from 'components';
import { read } from 'fs';
import { encode } from 'punycode';

export default function TableView({data, errorMessage}) {
  if(!data || typeof data !== 'object') {
    return <p>{errorMessage}</p>;
  }

  return <TableRenderer data={formatData(data)} />;
}

function formatData(data) {
  if (data.length) {
    // raw data
    let header = Object.keys(data[0]).filter(entry => entry !== 'variables');
    const variableNames = Object.keys(data[0].variables).map(varName => `Variable: ${varName}`);
    Array.prototype.push.apply(header,variableNames);
    const body = data.map(instance => {
      let row = Object.values(instance).filter(h => typeof h !== 'object' || h === null);
      const variableValues = Object.values(instance.variables);
      Array.prototype.push.apply(row, variableValues);
      row = row.map(entry => entry === null ? '': entry.toString());
      return row;
    });

    return [
      header,
      ...body
    ];
  } else {
    // normal two-dimensional data
    return Object.keys(data).map(key => [
      key,
      data[key]
    ]);
  }
}
