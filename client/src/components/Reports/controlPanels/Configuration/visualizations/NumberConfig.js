/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Switch, LabeledInput} from 'components';

import CountTargetInput from './subComponents/CountTargetInput';
import DurationTargetInput from './subComponents/DurationTargetInput';
import {t} from 'translation';

export default function NumberConfig({report, onChange}) {
  const {configuration, view, definitions} = report.data;
  const targetValue = configuration.targetValue;
  const isPercentageReport = view.properties.includes('percentage');

  const precisionSet = typeof configuration.precision === 'number';
  const countOperation =
    view.properties.includes('frequency') || isPercentageReport || view.entity === 'variable';
  const isMultiMeasure = report.result?.measures.length > 1;
  const isSingleProcessReport = definitions.length === 1;

  return (
    <div className="NumberConfig">
      {!isMultiMeasure && (
        <fieldset>
          <legend>
            <Switch
              checked={targetValue.active}
              onChange={(evt) => {
                const isActive = evt.target.checked;
                onChange({
                  targetValue: {
                    active: {$set: isActive},
                    isKpi: {$set: isActive && isSingleProcessReport},
                  },
                });
              }}
              label={t('report.config.goal.legend')}
            />
          </legend>
          {countOperation ? (
            <CountTargetInput
              baseline={targetValue.countProgress.baseline}
              target={targetValue.countProgress.target}
              isBelow={targetValue.countProgress.isBelow}
              disabled={!targetValue.active}
              isPercentageReport={isPercentageReport}
              onChange={(type, value) =>
                onChange({targetValue: {countProgress: {[type]: {$set: value}}}})
              }
            />
          ) : (
            <DurationTargetInput
              baseline={targetValue.durationProgress.baseline}
              target={targetValue.durationProgress.target}
              disabled={!targetValue.active}
              onChange={(type, subType, value) =>
                onChange({targetValue: {durationProgress: {[type]: {[subType]: {$set: value}}}}})
              }
            />
          )}
          {report.reportType !== 'decision' && isSingleProcessReport && (
            <>
              <LabeledInput
                disabled={!targetValue.active}
                type="checkbox"
                checked={!targetValue.active || targetValue.isKpi}
                onChange={(evt) => onChange({targetValue: {isKpi: {$set: evt.target.checked}}})}
                label={t('report.config.goal.setKpi')}
              />
              <p>{t('report.config.goal.kpiDescription')}</p>
            </>
          )}
        </fieldset>
      )}
      {!isPercentageReport && (
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
    </div>
  );
}
