import React from 'react';
import ColumnSelection from './subComponents/ColumnSelection';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import ShowInstanceCount from './subComponents/ShowInstanceCount';

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
        <RelativeAbsoluteSelection configuration={configuration} onChange={onChange} />
      );
      break;
    default:
      typeSpecificComponent = null;
  }

  return (
    <>
      {!report.combined && <ShowInstanceCount configuration={configuration} onChange={onChange} />}
      {typeSpecificComponent}
    </>
  );
}

TableConfig.defaults = {
  excludedColumns: [],
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  showInstanceCount: false
};

TableConfig.onUpdate = (prevProps, props) => {
  if (props.report.combined) return prevProps.type !== props.type && TableConfig.defaults;
  // if the view operation or report visualization changes, we need to reset to defaults
  if (
    (prevProps.report.data.view &&
      props.report.data.view &&
      prevProps.report.data.view.operation !== props.report.data.view.operation) ||
    prevProps.type !== props.type
  ) {
    return TableConfig.defaults;
  }
};

// disable popover for duration tables since they currently have no configuration
TableConfig.isDisabled = report => {
  return (
    report.combined &&
    report.data.reportIds &&
    report.data.reportIds.length &&
    Object.values(report.result)[0].data.view.property === 'duration'
  );
};
