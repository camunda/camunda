import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

export default function HeatmapConfig(props) {
  const {
    report: {data},
    onChange
  } = props;
  return (
    <fieldset>
      <legend>Always show tooltips</legend>
      <RelativeAbsoluteSelection
        hideRelative={data.view.property !== 'frequency'}
        absolute={data.configuration.alwaysShowAbsolute}
        relative={data.configuration.alwaysShowRelative}
        onChange={(type, value) => {
          if (type === 'absolute') {
            onChange({alwaysShowAbsolute: {$set: value}});
          } else {
            onChange({alwaysShowRelative: {$set: value}});
          }
        }}
      />
    </fieldset>
  );
}
