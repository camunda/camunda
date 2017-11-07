import React from 'react';

import {Table as TableRenderer} from 'components';

export default function Table({data}) {
  if(!data || typeof data !== 'object') {
    return <p>Cannot display data. Choose another visualization.</p>;
  }

  return <TableRenderer data={formatData(data)} />;
}

function formatData(data) {
  if (data.length) {
    // raw data
    const header = Object.keys(data[0]);
    const body = data.map(instance => Object.keys(instance).map(key => instance[key]));

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
