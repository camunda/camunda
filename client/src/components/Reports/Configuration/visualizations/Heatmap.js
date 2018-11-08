import React from 'react';
import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';

import './Heatmap.scss';

export default function Heatmap(props) {
  const {report, configuration, onChange} = props;
  return (
    <fieldset className="alwaysShowTooltips">
      <legend>Always show tooltips</legend>
      <RelativeAbsoluteSelection
        relativeDisabled={report.data.view.property !== 'frequency'}
        configuration={configuration}
        onChange={onChange}
      />
    </fieldset>
  );
}

Heatmap.defaults = {
  hideRelativeValue: true,
  hideAbsoluteValue: true
};
