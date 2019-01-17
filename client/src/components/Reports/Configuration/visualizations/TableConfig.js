import React from 'react';
import ColumnSelection from './subComponents/ColumnSelection';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

export default function TableConfig({report, configuration, onChange}) {
  let typeSpecificComponent = null;
  const viewType = !report.combined
    ? report.data.view.operation
    : Object.values(report.result)[0].data.view.operation;

  switch (viewType) {
    case 'rawData':
      typeSpecificComponent = <ColumnSelection report={report} onChange={onChange} />;
      break;
    case 'count':
      typeSpecificComponent = (
        <RelativeAbsoluteSelection
          absolute={!configuration.hideAbsoluteValue}
          relative={!configuration.hideRelativeValue}
          onChange={(type, value) => {
            if (type === 'absolute') {
              onChange({hideAbsoluteValue: {$set: !value}});
            } else {
              onChange({hideRelativeValue: {$set: !value}});
            }
          }}
        />
      );
      break;
    default:
      typeSpecificComponent = null;
  }

  return typeSpecificComponent;
}

// // disable popover for duration tables since they currently have no configuration
TableConfig.isDisabled = report => {
  return (
    report.combined &&
    report.data.reportIds &&
    report.data.reportIds.length &&
    Object.values(report.result)[0].data.view.property === 'duration'
  );
};
