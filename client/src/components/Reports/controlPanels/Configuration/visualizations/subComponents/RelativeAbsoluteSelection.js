/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch} from 'components';
import {t} from 'translation';

export default function RelativeAbsoluteSelection({hideRelative, absolute, relative, onChange}) {
  return (
    <>
      <label>
        <Switch
          checked={absolute}
          onChange={({target: {checked}}) => onChange('absolute', checked)}
        />
        {t('report.config.tooltips.showAbsolute')}
      </label>
      {!hideRelative && (
        <label>
          <Switch
            checked={relative}
            onChange={({target: {checked}}) => onChange('relative', checked)}
          />
          {t('report.config.tooltips.showRelative')}
        </label>
      )}
    </>
  );
}
