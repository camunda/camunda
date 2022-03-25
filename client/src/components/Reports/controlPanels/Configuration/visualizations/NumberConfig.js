/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Switch, LabeledInput} from 'components';

import CountTargetInput from './subComponents/CountTargetInput';
import DurationTargetInput from './subComponents/DurationTargetInput';
import {t} from 'translation';

export default function NumberConfig({report, onChange}) {
  const {configuration, view} = report.data;
  const targetValue = configuration.targetValue;

  const precisionSet = typeof configuration.precision === 'number';
  const countOperation =
    view.properties.includes('frequency') ||
    view.properties.includes('percentage') ||
    view.entity === 'variable';
  const goalSet = targetValue.active;

  const isMultiMeasure = view.properties.length > 1 || configuration.aggregationTypes.length > 1;

  return (
    <div className="NumberConfig">
      {!view.properties.includes('percentage') && (
        <fieldset>
          <legend>
            <Switch
              checked={precisionSet}
              onChange={(evt) => onChange({precision: {$set: evt.target.checked ? 1 : null}})}
              label={t('report.config.limitPrecision.legend')}
            />
          </legend>
          <LabeledInput
            className="precision"
            label={t(
              `report.config.limitPrecision.numberOf.${countOperation ? 'digits' : 'units'}`
            )}
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
      )}
      {!isMultiMeasure && (
        <fieldset>
          <legend>
            <Switch
              checked={goalSet}
              onChange={(evt) => onChange({targetValue: {active: {$set: evt.target.checked}}})}
              label={t('report.config.goal.legend')}
            />
          </legend>
          {countOperation ? (
            <CountTargetInput
              baseline={targetValue.countProgress.baseline}
              target={targetValue.countProgress.target}
              disabled={!goalSet}
              onChange={(type, value) =>
                onChange({targetValue: {countProgress: {[type]: {$set: value}}}})
              }
            />
          ) : (
            <DurationTargetInput
              baseline={targetValue.durationProgress.baseline}
              target={targetValue.durationProgress.target}
              disabled={!goalSet}
              onChange={(type, subType, value) =>
                onChange({targetValue: {durationProgress: {[type]: {[subType]: {$set: value}}}}})
              }
            />
          )}
        </fieldset>
      )}
    </div>
  );
}
