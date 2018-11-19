import React from 'react';

import {Switch} from 'components';

export default function RelativeAbsoluteSelection({relativeDisabled, configuration, onChange}) {
  const disabledMarkers = configuration.pointMarkers === false;
  return (
    <div className="RelativeAbsoluteSelection">
      <div className="entry">
        <Switch
          disabled={disabledMarkers}
          checked={!configuration.hideAbsoluteValue && !disabledMarkers}
          onChange={({target: {checked}}) => onChange('hideAbsoluteValue', !checked)}
        />
        Show Absolute Value
      </div>
      <div className="entry">
        <Switch
          disabled={relativeDisabled || disabledMarkers}
          title={
            relativeDisabled
              ? 'Relative values are only possible on reports with "count frequency" view'
              : undefined
          }
          checked={!configuration.hideRelativeValue && !relativeDisabled && !disabledMarkers}
          onChange={({target: {checked}}) => onChange('hideRelativeValue', !checked)}
        />
        Show Relative Value
      </div>
    </div>
  );
}
