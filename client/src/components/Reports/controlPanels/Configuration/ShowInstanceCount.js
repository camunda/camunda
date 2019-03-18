import React from 'react';

import {Switch} from 'components';

export default function ShowInstanceCount({configuration, onChange, label}) {
  return (
    <>
      <Switch
        checked={!!configuration.showInstanceCount}
        onChange={({target: {checked}}) => onChange({showInstanceCount: {$set: checked}})}
      />
      Show {label} Count
    </>
  );
}
