/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Icon, LabeledInput, Switch, Tooltip} from 'components';
import {t} from 'translation';

import './PrecisionConfig.scss';

export default function PrecisionConfig({configuration, onChange, view, type}) {
  const precisionSet = typeof configuration.precision === 'number';
  const countOperation =
    view &&
    (view.properties.includes('frequency') ||
      view.properties.includes('percentage') ||
      view.entity === 'variable');

  return (
    <fieldset className="PrecisionConfig">
      <legend>
        <Switch
          checked={precisionSet}
          onChange={(evt) => onChange({precision: {$set: evt.target.checked ? 1 : null}})}
          label={t('report.config.limitPrecision.legend')}
        />
        {type !== 'number' && type !== 'table' && (
          <Tooltip content={t('report.config.limitPrecision.tooltip')}>
            <Icon type="info" />
          </Tooltip>
        )}
      </legend>
      <LabeledInput
        className="precision"
        label={t(`report.config.limitPrecision.numberOf.${countOperation ? 'digits' : 'units'}`)}
        disabled={typeof configuration.precision !== 'number'}
        onKeyDown={(evt) => {
          const number = parseInt(evt.key, 10);
          if (number) {
            onChange({precision: {$set: number}});
          }
        }}
        value={precisionSet ? configuration.precision : 1}
      />
    </fieldset>
  );
}
