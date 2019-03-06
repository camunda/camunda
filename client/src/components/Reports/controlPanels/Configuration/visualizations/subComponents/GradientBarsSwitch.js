import React from 'react';

import {Switch} from 'components';

export default function GradientBarsSwitch({configuration, onChange}) {
  return (
    <>
      <Switch
        checked={!!configuration.showGradientBars}
        onChange={({target: {checked}}) => onChange({showGradientBars: {$set: checked}})}
      />
      Show Gradient Bars
    </>
  );
}
