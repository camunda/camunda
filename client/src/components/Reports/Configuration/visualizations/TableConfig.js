import React from 'react';
import ColumnSelection from './subComponents/ColumnSelection';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import ShowInstanceCount from './subComponents/ShowInstanceCount';

export default function TableConfig({report, configuration, onChange}) {
  let typeSpecificComponent = null;
  switch (report.data.view.operation) {
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
      <ShowInstanceCount configuration={configuration} onChange={onChange} />
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
  // if the view operation changes, we need to reset to defaults
  if (
    prevProps.report.data.view &&
    props.report.data.view &&
    prevProps.report.data.view.operation !== props.report.data.view.operation
  ) {
    return TableConfig.defaults;
  }
};
