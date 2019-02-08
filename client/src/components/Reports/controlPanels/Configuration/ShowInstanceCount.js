import React from 'react';

import {Switch} from 'components';

export default function ShowInstanceCount({configuration, onChange}) {
  return (
    <>
      <Switch
        checked={!!configuration.showInstanceCount}
        onChange={({target: {checked}}) => onChange({showInstanceCount: {$set: checked}})}
      />
      Show Instance Count
    </>
  );
}
