/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch} from 'components';

export default function RelativeAbsoluteSelection({hideRelative, absolute, relative, onChange}) {
  return (
    <div className="RelativeAbsoluteSelection">
      <div className="entry">
        <Switch
          checked={absolute}
          onChange={({target: {checked}}) => onChange('absolute', checked)}
        />
        Show Absolute Value
      </div>
      {!hideRelative && (
        <div className="entry">
          <Switch
            checked={relative}
            onChange={({target: {checked}}) => onChange('relative', checked)}
          />
          Show Relative Value
        </div>
      )}
    </div>
  );
}
