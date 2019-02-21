import React from 'react';
import ColumnSelection from './subComponents/ColumnSelection';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

import {isDurationReport} from 'services';

export default function TableConfig({report, onChange}) {
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
          absolute={!report.data.configuration.hideAbsoluteValue}
          relative={!report.data.configuration.hideRelativeValue}
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
    report.data.reports &&
    report.data.reports.length &&
    isDurationReport(Object.values(report.result)[0])
  );
};
