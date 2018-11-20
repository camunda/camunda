import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import ShowInstanceCount from './subComponents/ShowInstanceCount';

export default function HeatmapConfig(props) {
  const {report, configuration, onChange} = props;
  return (
    <>
      <ShowInstanceCount configuration={configuration} onChange={onChange} />
      <fieldset>
        <legend>Always show tooltips</legend>
        <RelativeAbsoluteSelection
          relativeDisabled={report.data.view.property !== 'frequency'}
          configuration={configuration}
          onChange={onChange}
        />
      </fieldset>
    </>
  );
}

HeatmapConfig.defaults = {
  hideRelativeValue: true,
  hideAbsoluteValue: true,
  showInstanceCount: false,
  targetValue: null
};

HeatmapConfig.onUpdate = (prevProps, props) => {
  if (
    props.report.data.view.property !== prevProps.report.data.view.property ||
    prevProps.type !== props.type
  ) {
    return HeatmapConfig.defaults;
  }
};
