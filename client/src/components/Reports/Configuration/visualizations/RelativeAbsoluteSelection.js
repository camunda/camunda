import React from 'react';

import {Switch} from 'components';

export default function RelativeAbsoluteSelection({configuration, onChange}) {
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
          checked={!configuration.hideRelativeValue}
          onChange={({target: {checked}}) => onChange('hideRelativeValue', !checked)}
        />
        Show Relative Value
      </div>
    </div>
  );
}
