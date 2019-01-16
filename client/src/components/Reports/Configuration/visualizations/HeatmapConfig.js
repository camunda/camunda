import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

export default function HeatmapConfig(props) {
  const {report, configuration, onChange} = props;
  return (
    <fieldset>
      <legend>Always show tooltips</legend>
      <RelativeAbsoluteSelection
        hideRelative={report.data.view.property !== 'frequency'}
        absolute={configuration.alwaysShowAbsolute}
        relative={configuration.alwaysShowRelative}
        onChange={(type, value) => {
          if (type === 'absolute') {
            onChange('alwaysShowAbsolute', value);
          } else {
            onChange('alwaysShowRelative', value);
          }
        }}
      />
    </fieldset>
  );
}
