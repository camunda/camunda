/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Switch} from 'components';
import {t} from 'translation';

export default function PointMarkersConfig({configuration, onChange}) {
  return (
    <fieldset className="PointMarkersConfig">
      <legend>{t('report.config.pointMarkers.legend')}</legend>
      <Switch
        checked={configuration.pointMarkers}
        onChange={({target: {checked}}) => onChange({pointMarkers: {$set: checked}})}
        label={t('report.config.pointMarkers.enableMarkers')}
      />
    </fieldset>
  );
}
