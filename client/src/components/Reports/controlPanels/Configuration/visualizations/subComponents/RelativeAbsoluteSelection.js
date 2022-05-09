/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Switch} from 'components';
import {t} from 'translation';

export default function RelativeAbsoluteSelection({
  hideRelative,
  absolute,
  relative,
  reportType,
  onChange,
}) {
  return (
    <>
      <Switch
        checked={absolute}
        onChange={({target: {checked}}) => onChange('absolute', checked)}
        label={t('report.config.tooltips.showAbsolute')}
      />

      {!hideRelative && (
        <Switch
          checked={relative}
          onChange={({target: {checked}}) => onChange('relative', checked)}
          label={t('report.config.tooltips.showRelative.' + reportType)}
        />
      )}
    </>
  );
}
