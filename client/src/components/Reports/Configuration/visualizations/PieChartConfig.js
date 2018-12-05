import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

export default function PieChartConfig({configuration, onChange, report}) {
  return (
    <fieldset className="tooltipOptions">
      <legend>Tooltips options</legend>
      <RelativeAbsoluteSelection
        relativeDisabled={report.data.view.property !== 'frequency'}
        configuration={configuration}
        onChange={onChange}
      />
    </fieldset>
  );
}

PieChartConfig.defaults = {
  hideRelativeValue: false,
  hideAbsoluteValue: false
};

PieChartConfig.onUpdate = (prevProps, props) => {
  const currentView = props.report.data.view;
  const prevView = prevProps.report.data.view;
  if (
    currentView.property !== prevView.property ||
    currentView.entity !== prevView.entity ||
    prevProps.type !== props.type
  ) {
    return PieChartConfig.defaults;
  }
};
