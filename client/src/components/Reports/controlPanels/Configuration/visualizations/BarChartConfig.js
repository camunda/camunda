/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FormGroup, Stack, TextInput, Toggle} from '@carbon/react';

import {ColorPicker} from 'components';
import {isDurationReport} from 'services';
import {t} from 'translation';

import ChartTargetInput from './subComponents/ChartTargetInput';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import './BarChartConfig.scss';

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
  const isHorizontalPossible = !combined && visualization === 'bar';
  const isStacked = isStackingPossible && configuration.stackedBar;
  const isHorizontal = configuration.horizontalBar;

  return (
    <Stack gap={4} className="BarChartConfig">
      {!combined &&
        !isMultiMeasure &&
        (distributedBy.type === 'none' ||
          (distributedBy.type === 'process' && groupBy.type === 'none')) && (
          <FormGroup legendText={t('report.config.colorPicker.legend')} className="colorSection">
            <ColorPicker
              selectedColor={configuration.color}
              onChange={(color) => onChange({color: {$set: color}})}
            />
          </FormGroup>
        )}
      <FormGroup legendText={t('report.config.tooltips.legend')}>
        <Stack gap={4}>
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
        </Stack>
      </FormGroup>

      {(isStackingPossible || isHorizontalPossible) && (
        <FormGroup legendText={t('report.config.display.legend')} className="display">
          <Stack gap={4}>
            {isHorizontalPossible && (
              <Toggle
                id="horizontalBarsToggle"
                size="sm"
                toggled={configuration.horizontalBar}
                onToggle={(checked) => onChange({horizontalBar: {$set: checked}})}
                labelA={t('report.config.display.horizontalBars')}
                labelB={t('report.config.display.horizontalBars')}
              />
            )}
            {isStackingPossible && (
              <Toggle
                id="stackBarsToggle"
                size="sm"
                toggled={configuration.stackedBar}
                onToggle={(checked) => onChange({stackedBar: {$set: checked}})}
                labelA={t('report.config.display.enableStackedBars')}
                labelB={t('report.config.display.enableStackedBars')}
              />
            )}
          </Stack>
        </FormGroup>
      )}
      <FormGroup legendText={t('report.config.axisSettings.legend')}>
        <Stack gap={4}>
          <Toggle
            id="axisSettingsToggle"
            size="sm"
            toggled={configuration.logScale}
            onToggle={(checked) => onChange({logScale: {$set: checked}})}
            labelA={t('report.config.axisSettings.logScale')}
            labelB={t('report.config.axisSettings.logScale')}
          />
          <TextInput
            id="axis1LabelInput"
            labelText={t('report.config.axisSettings.' + (isHorizontal ? 'yAxis' : 'xAxis'))}
            value={configuration.xLabel}
            onChange={({target: {value}}) => onChange({xLabel: {$set: value}})}
          />
          {!isMultiMeasure && (
            <TextInput
              id="axis2LabelInput"
              labelText={t('report.config.axisSettings.' + (isHorizontal ? 'xAxis' : 'yAxis'))}
              value={configuration.yLabel}
              onChange={({target: {value}}) => onChange({yLabel: {$set: value}})}
            />
          )}
        </Stack>
      </FormGroup>
      {!isMultiMeasure && !isStacked && (
        <FormGroup
          legendText={
            <Toggle
              id="setTargetToggle"
              size="sm"
              toggled={configuration.targetValue.active}
              onToggle={(checked) => onChange({targetValue: {active: {$set: checked}}})}
              labelA={t('report.config.goal.legend')}
              labelB={t('report.config.goal.legend')}
            />
          }
        >
          <ChartTargetInput {...{onChange, report}} />
        </FormGroup>
      )}
    </Stack>
  );
}
