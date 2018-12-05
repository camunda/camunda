import React from 'react';

import {Switch} from 'components';

export default function RelativeAbsoluteSelection({relativeDisabled, configuration, onChange}) {
  return (
    <div className="RelativeAbsoluteSelection">
      <div className="entry">
        <Switch
          checked={!configuration.hideAbsoluteValue}
          onChange={({target: {checked}}) => onChange('hideAbsoluteValue', !checked)}
        />
        Show Absolute Value
      </div>
      <div className="entry">
        <Switch
          disabled={relativeDisabled}
          title={
            relativeDisabled
              ? 'Relative values are only possible on reports with "count frequency" view'
              : undefined
          }
          checked={!configuration.hideRelativeValue && !relativeDisabled}
          onChange={({target: {checked}}) => onChange('hideRelativeValue', !checked)}
        />
        Show Relative Value
      </div>
    </div>
  );
}
