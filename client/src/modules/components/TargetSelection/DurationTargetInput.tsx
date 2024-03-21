/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {RadioButton, RadioButtonGroup, Stack, TextInput} from '@carbon/react';

import {CarbonSelect} from 'components';
import {formatters, numberParser} from 'services';
import {t} from 'translation';

import './DurationTargetInput.scss';

const {isNonNegativeNumber} = numberParser;
const {convertDurationToSingleNumber} = formatters;

interface DurationTargetInputProps {
  baseline: {value: string | number; unit: string};
  target: {value: string | number; unit: string; isBelow?: boolean};
  disabled?: boolean;
  onChange: (type: string, subType: string, value: boolean | string) => void;
}

export default function DurationTargetInput({
  baseline,
  target,
  disabled,
  onChange,
}: DurationTargetInputProps) {
  const baselineInvalid = !isNonNegativeNumber(baseline.value);
  const targetInvalid = !isNonNegativeNumber(target.value);

  const tooLow =
    !baselineInvalid &&
    !targetInvalid &&
    convertDurationToSingleNumber(target) <= convertDurationToSingleNumber(baseline);

  return (
    <Stack gap={4} className="DurationTargetInput">
      <RadioButtonGroup name="durationTargetRadio" disabled={disabled}>
        <RadioButton
          value="false"
          checked={!target.isBelow}
          labelText={t('common.above')}
          onClick={() => onChange('target', 'isBelow', false)}
        />
        <RadioButton
          value="true"
          checked={target.isBelow}
          labelText={t('common.below')}
          onClick={() => onChange('target', 'isBelow', true)}
        />
      </RadioButtonGroup>
      <Stack gap={4} orientation="horizontal">
        <TextInput
          id="targetValueInput"
          labelText={t('report.config.goal.target')}
          type="number"
          min="0"
          value={target.value}
          disabled={disabled}
          invalid={targetInvalid || tooLow}
          invalidText={
            tooLow
              ? t('report.config.goal.lessThanTargetError')
              : t('report.config.goal.invalidInput')
          }
          onChange={(evt) => onChange('target', 'value', evt.target.value)}
        />
        <CarbonSelect
          labelText={t('common.units')}
          size="md"
          id="targetUnitSelector"
          value={target.unit}
          disabled={disabled}
          onChange={(value) => onChange('target', 'unit', value)}
        >
          {selectionOptions()}
        </CarbonSelect>
      </Stack>
      <Stack gap={4} orientation="horizontal">
        <TextInput
          id="baselineValueInput"
          labelText={t('report.config.goal.baseline')}
          type="number"
          min="0"
          value={baseline.value}
          disabled={disabled}
          invalid={baselineInvalid}
          invalidText={t('report.config.goal.invalidInput')}
          onChange={(evt) => onChange('baseline', 'value', evt.target.value)}
        />
        <CarbonSelect
          labelText={t('common.units')}
          size="md"
          id="baselineUnitSelector"
          value={baseline.unit}
          disabled={disabled}
          onChange={(value) => onChange('baseline', 'unit', value)}
        >
          {selectionOptions()}
        </CarbonSelect>
      </Stack>
    </Stack>
  );
}

function selectionOptions() {
  return (
    <>
      <CarbonSelect.Option value="millis" label={t('common.unit.milli.label-plural')} />
      <CarbonSelect.Option value="seconds" label={t('common.unit.second.label-plural')} />
      <CarbonSelect.Option value="minutes" label={t('common.unit.minute.label-plural')} />
      <CarbonSelect.Option value="hours" label={t('common.unit.hour.label-plural')} />
      <CarbonSelect.Option value="days" label={t('common.unit.day.label-plural')} />
      <CarbonSelect.Option value="weeks" label={t('common.unit.week.label-plural')} />
      <CarbonSelect.Option value="months" label={t('common.unit.month.label-plural')} />
      <CarbonSelect.Option value="years" label={t('common.unit.year.label-plural')} />
    </>
  );
}
