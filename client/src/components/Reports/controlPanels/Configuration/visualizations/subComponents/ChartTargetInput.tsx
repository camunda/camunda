/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {NumberInput, RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {CarbonSelect} from 'components';
import {GenericReport} from 'types';
import {numberParser} from 'services';
import {t} from 'translation';

interface ChartTargetInputProps {
  onChange: (change: {
    targetValue: Record<string, Record<string, {$set: string | number | boolean}>>;
  }) => void;
  report: GenericReport;
}

export default function ChartTargetInput({onChange, report}: ChartTargetInputProps) {
  const {
    configuration: {targetValue},
  } = report.data;
  const referenceReport = report.combined ? Object.values(report.result.data)[0] : report;
  const isCountReport = ['frequency', 'percentage'].includes(
    referenceReport?.data.view.properties[0] as string
  );
  const type = isCountReport ? 'countChart' : 'durationChart';

  function setValues(prop: string, value: string | number | boolean) {
    onChange({
      targetValue: {
        [type]: {
          [prop]: {$set: value},
        },
      },
    });
  }

  const isInvalid = !numberParser.isNonNegativeNumber(targetValue[type]?.value);

  return (
    <Stack gap={4}>
      <RadioButtonGroup name="chartTargetRadio" disabled={!targetValue.active}>
        <RadioButton
          value="false"
          checked={!targetValue[type]?.isBelow}
          labelText={t('common.above')}
          onClick={() => setValues('isBelow', false)}
        />
        <RadioButton
          value="true"
          checked={targetValue[type]?.isBelow}
          labelText={t('common.below')}
          onClick={() => setValues('isBelow', true)}
        />
      </RadioButtonGroup>
      <NumberInput
        id="goalValueInput"
        min={0}
        label={t('report.config.goal.goalValue')}
        value={targetValue[type]?.value}
        onChange={(evt, {value}) => setValues('value', value)}
        invalid={isInvalid}
        invalidText={t('report.config.goal.invalidInput')}
        disabled={!targetValue.active}
      />
      {type === 'durationChart' && (
        <CarbonSelect
          id="durationChartUnits"
          value={targetValue[type]?.unit}
          onChange={(value) => setValues('unit', value)}
          disabled={!targetValue.active}
          size="md"
        >
          <CarbonSelect.Option value="millis" label={t('common.unit.milli.label-plural')} />
          <CarbonSelect.Option value="seconds" label={t('common.unit.second.label-plural')} />
          <CarbonSelect.Option value="minutes" label={t('common.unit.minute.label-plural')} />
          <CarbonSelect.Option value="hours" label={t('common.unit.hour.label-plural')} />
          <CarbonSelect.Option value="days" label={t('common.unit.day.label-plural')} />
          <CarbonSelect.Option value="weeks" label={t('common.unit.week.label-plural')} />
          <CarbonSelect.Option value="months" label={t('common.unit.month.label-plural')} />
          <CarbonSelect.Option value="years" label={t('common.unit.year.label-plural')} />
        </CarbonSelect>
      )}
    </Stack>
  );
}
