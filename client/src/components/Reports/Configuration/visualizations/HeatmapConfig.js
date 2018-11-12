import React from 'react';
import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';
import ShowInstanceCount from './ShowInstanceCount';

import './HeatmapConfig.scss';

export default function HeatmapConfig(props) {
  const {report, configuration, onChange} = props;
  return (
    <>
      <ShowInstanceCount configuration={configuration} onChange={onChange} />
      <fieldset className="alwaysShowTooltips">
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
  showInstanceCount: false
};
