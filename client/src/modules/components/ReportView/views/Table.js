import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import {Table as TableRenderer} from 'components';
import {processRawData} from 'services';

export default function Table({
  data,
  configuration: {excludedColumns, columnOrder},
  formatter = v => v,
  labels,
  errorMessage,
  disableReportScrolling
}) {
  if (!data || typeof data !== 'object') {
    return <ReportBlankSlate message={errorMessage} />;
  }

  return (
    <TableRenderer
      disableReportScrolling={disableReportScrolling}
      {...formatData(data, formatter, labels, excludedColumns, columnOrder)}
    />
  );
}

export function formatData(data, formatter, labels, excludedColumns, columnOrder) {
  if (data.length) {
    // raw data
    return processRawData(data, excludedColumns, columnOrder);
  } else {
    // normal two-dimensional data
    const body = Object.keys(data).map(key => [key, formatter(data[key])]);
    return {head: labels, body};
  }
}
