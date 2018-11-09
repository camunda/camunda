import React from 'react';
import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';
import ShowInstanceCount from './ShowInstanceCount';

import './Heatmap.scss';

export default function Heatmap(props) {
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

Heatmap.defaults = {
  hideRelativeValue: true,
  hideAbsoluteValue: true,
  showInstanceCount: false
};
