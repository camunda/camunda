/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {ColorPicker, Switch, Input} from 'components';
import ChartTargetInput from './subComponents/ChartTargetInput';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import {isDurationReport} from 'services';
import './BarChartConfig.scss';
import {t} from 'translation';

export default function BarChartConfig({onChange, report}) {
  const {
    reportType,
    combined,
    data: {configuration, groupBy, distributedBy, visualization},
    result,
  } = report;

  const durationReport = isDurationReport(combined ? Object.values(result.data)[0] : report);
  const isMultiMeasure = combined ? false : result?.measures.length > 1;
  const isStackingPossible =
    !combined &&
    distributedBy.type !== 'none' &&
    groupBy.type !== 'none' &&
    ['barLine', 'bar'].includes(visualization);
  const isStacked = isStackingPossible && configuration.stackedBar;

  return (
    <div className="BarChartConfig">
      {!combined &&
        !isMultiMeasure &&
        (distributedBy.type === 'none' ||
          (distributedBy.type === 'process' && groupBy.type === 'none')) && (
          <fieldset className="colorSection">
            <legend>{t('report.config.colorPicker.legend')}</legend>
            <ColorPicker
              selectedColor={configuration.color}
              onChange={(color) => onChange({color: {$set: color}})}
            />
          </fieldset>
        )}
      <fieldset>
        <legend>{t('report.config.tooltips.legend')}</legend>
        <RelativeAbsoluteSelection
          reportType={reportType}
          hideRelative={durationReport}
          absolute={configuration.alwaysShowAbsolute}
          relative={configuration.alwaysShowRelative}
          onChange={(type, value) => {
            if (type === 'absolute') {
              onChange({alwaysShowAbsolute: {$set: value}});
            } else {
              onChange({alwaysShowRelative: {$set: value}});
            }
          }}
        />
      </fieldset>
      {isStackingPossible && (
        <fieldset className="stackedBars">
          <legend>{t('report.config.stackedBars.legend')}</legend>
          <Switch
            checked={configuration.stackedBar}
            onChange={({target: {checked}}) => onChange({stackedBar: {$set: checked}})}
            label={t('report.config.stackedBars.enableStackedBars')}
          />
        </fieldset>
      )}
      <fieldset>
        <legend>{t('report.config.axisSettings.legend')}</legend>
        <Switch
          checked={configuration.logScale}
          onChange={({target: {checked}}) => onChange({logScale: {$set: checked}})}
          label={t('report.config.axisSettings.logScale')}
        />
        <label>{t('report.config.axisSettings.label')}</label>
        <Input
          placeholder={t('report.config.axisSettings.xAxis')}
          type="text"
          value={configuration.xLabel}
          onChange={({target: {value}}) => onChange({xLabel: {$set: value}})}
        />
        {!isMultiMeasure && (
          <Input
            placeholder={t('report.config.axisSettings.yAxis')}
            type="text"
            value={configuration.yLabel}
            onChange={({target: {value}}) => onChange({yLabel: {$set: value}})}
          />
        )}
      </fieldset>
      {!isMultiMeasure && !isStacked && (
        <fieldset>
          <legend>
            <Switch
              checked={configuration.targetValue.active}
              onChange={({target: {checked}}) => onChange({targetValue: {active: {$set: checked}}})}
              label={t('report.config.goal.legend')}
            />
          </legend>
          <ChartTargetInput {...{onChange, report}} />
        </fieldset>
      )}
    </div>
  );
}
