/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
