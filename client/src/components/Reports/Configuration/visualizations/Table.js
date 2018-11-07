import React from 'react';
import ColumnSelection from './ColumnSelection';
import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';

export default function Table({report, configuration, onChange}) {
  switch (report.data.view.operation) {
    case 'rawData':
      return <ColumnSelection report={report} onChange={onChange} />;
    case 'count':
      return <RelativeAbsoluteSelection configuration={configuration} onChange={onChange} />;
    default:
      return null;
  }
}

Table.defaults = {
  excludedColumns: [],
  hideRelativeValue: false,
  hideAbsoluteValue: false
};

Table.onUpdate = (prevProps, props) => {
  // if the view operation changes, we need to reset to defaults
  if (
    prevProps.report.data.view &&
    props.report.data.view &&
    prevProps.report.data.view.operation !== props.report.data.view.operation
  ) {
    return Table.defaults;
  }
};
