/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Button, ButtonGroup, Form, LabeledInput, Message, Select} from 'components';

import {formatters, numberParser} from 'services';
import {t} from 'translation';

const {isNonNegativeNumber} = numberParser;
const {convertDurationToSingleNumber} = formatters;

export default function DurationTargetInput({baseline, target, disabled, onChange}) {
  const baselineInvalid = !isNonNegativeNumber(baseline.value);
  const targetInvalid = !isNonNegativeNumber(target.value);

  const tooLow =
    !baselineInvalid &&
    !targetInvalid &&
    convertDurationToSingleNumber(target) <= convertDurationToSingleNumber(baseline);

  return (
    <>
      <ButtonGroup disabled={disabled}>
        <Button onClick={() => onChange('target', 'isBelow', false)} active={!target.isBelow}>
          {t('common.above')}
        </Button>
        <Button onClick={() => onChange('target', 'isBelow', true)} active={target.isBelow}>
          {t('common.below')}
        </Button>
      </ButtonGroup>
      <Form.InputGroup>
        <LabeledInput
          label={t('report.config.goal.target')}
          type="number"
          min="0"
          value={target.value}
          disabled={disabled}
          isInvalid={targetInvalid || tooLow}
          onChange={(evt) => onChange('target', 'value', evt.target.value)}
        />
        <Select
          value={target.unit}
          disabled={disabled}
          onChange={(value) => onChange('target', 'unit', value)}
        >
          {selectionOptions()}
        </Select>
      </Form.InputGroup>
      {targetInvalid && <Message error>{t('report.config.goal.invalidInput')}</Message>}
      <Form.InputGroup className="DurationTargetInput">
        <LabeledInput
          label={t('report.config.goal.baseline')}
          type="number"
          min="0"
          value={baseline.value}
          disabled={disabled}
          isInvalid={baselineInvalid}
          onChange={(evt) => onChange('baseline', 'value', evt.target.value)}
        />
        <Select
          value={baseline.unit}
          disabled={disabled}
          onChange={(value) => onChange('baseline', 'unit', value)}
        >
          {selectionOptions()}
        </Select>
      </Form.InputGroup>
      {baselineInvalid && <Message error>{t('report.config.goal.invalidInput')}</Message>}
      {tooLow && <Message error>{t('report.config.goal.lessThanTargetError')}</Message>}
    </>
  );
}

function selectionOptions() {
  return (
    <>
      <Select.Option value="millis">{t('common.unit.milli.label-plural')}</Select.Option>
      <Select.Option value="seconds">{t('common.unit.second.label-plural')}</Select.Option>
      <Select.Option value="minutes">{t('common.unit.minute.label-plural')}</Select.Option>
      <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
      <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
      <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
      <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
      <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
    </>
  );
}
