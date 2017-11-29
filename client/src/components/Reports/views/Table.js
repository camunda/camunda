import React from 'react';

import {Table as TableRenderer} from 'components';

export default function Table({data, errorMessage}) {
  if(!data || typeof data !== 'object') {
    return <p>{errorMessage}</p>;
  }

  return <TableRenderer {...formatData(data)} />;
}

function formatData(data) {
  if (data.length) {
    // raw data
    const processInstanceProps = Object.keys(data[0]).filter(entry => entry !== 'variables');
    const rawVariableNames = Object.keys(data[0].variables);
  
    const body = data.map(instance => {
      let row = processInstanceProps.map(entry => instance[entry]);
      const variableValues = rawVariableNames.map(entry => instance.variables[entry]);
      row.push(...variableValues);
      row = row.map(entry => entry === null ? '': entry.toString());
      return row;
    });

    const variableNames = rawVariableNames.map(varName => `Variable: ${varName}`);
    const head = processInstanceProps;
    head.push(...variableNames)

    return {head, body};
  } else {
    // normal two-dimensional data
    const body = Object.keys(data).map(key => [
      key,
      data[key]
    ]);
    return {body};
  }
}
