/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {NumberInput, RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {Select} from 'components';
import {SingleProcessReport} from 'types';
import {numberParser} from 'services';
import {t} from 'translation';

interface ChartTargetInputProps {
  onChange: (change: {
    targetValue: Record<string, Record<string, {$set: string | number | boolean}>>;
  }) => void;
  report: SingleProcessReport;
}

export default function ChartTargetInput({onChange, report}: ChartTargetInputProps) {
  const {
    configuration: {targetValue},
  } = report.data;
  const isCountReport = ['frequency', 'percentage'].includes(
    report?.data.view?.properties[0] as string
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
        onChange={(_evt, {value}) => setValues('value', value)}
        invalid={isInvalid}
        invalidText={t('report.config.goal.invalidInput')}
        disabled={!targetValue.active}
      />
      {type === 'durationChart' && (
        <Select
          id="durationChartUnits"
          value={targetValue[type]?.unit}
          onChange={(value) => setValues('unit', value)}
          disabled={!targetValue.active}
          size="md"
        >
          <Select.Option value="millis" label={t('common.unit.milli.label-plural')} />
          <Select.Option value="seconds" label={t('common.unit.second.label-plural')} />
          <Select.Option value="minutes" label={t('common.unit.minute.label-plural')} />
          <Select.Option value="hours" label={t('common.unit.hour.label-plural')} />
          <Select.Option value="days" label={t('common.unit.day.label-plural')} />
          <Select.Option value="weeks" label={t('common.unit.week.label-plural')} />
          <Select.Option value="months" label={t('common.unit.month.label-plural')} />
          <Select.Option value="years" label={t('common.unit.year.label-plural')} />
        </Select>
      )}
    </Stack>
  );
}
