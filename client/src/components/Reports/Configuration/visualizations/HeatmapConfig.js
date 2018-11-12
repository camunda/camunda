import React from 'react';
import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';

import './HeatmapConfig.scss';

export default function HeatmapConfig(props) {
  const {report, configuration, onChange} = props;
  return (
    <div className="HeatmapConfig">
      <fieldset>
        <legend>Always show tooltips</legend>
        <RelativeAbsoluteSelection
          relativeDisabled={report.data.view.property !== 'frequency'}
          configuration={configuration}
          onChange={onChange}
        />
      </fieldset>
    </div>
  );
}

HeatmapConfig.defaults = {
  hideRelativeValue: true,
  hideAbsoluteValue: true
};
