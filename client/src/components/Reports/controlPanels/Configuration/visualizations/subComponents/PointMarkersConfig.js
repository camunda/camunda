/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Switch} from 'components';
import {t} from 'translation';

export default function PointMarkersConfig({configuration, onChange}) {
  return (
    <fieldset className="PointMarkersConfig">
      <legend>{t('report.config.pointMarkers.legend')}</legend>
      <label>
        <Switch
          checked={!configuration.pointMarkers}
          onChange={({target: {checked}}) => onChange({pointMarkers: {$set: !checked}})}
        />
        {t('report.config.pointMarkers.disableMarkers')}
      </label>
    </fieldset>
  );
}
