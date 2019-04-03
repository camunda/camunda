/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ControlGroup, LabeledInput, ErrorMessage, Select} from 'components';

import './DurationTargetInput.scss';

import {formatters, numberParser} from 'services';

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
    <ControlGroup className="DurationTargetInput">
      <LabeledInput
        label="Baseline"
        type="number"
        min="0"
        value={baseline.value}
        disabled={disabled}
        isInvalid={baselineInvalid}
        onChange={evt => onChange('baseline', 'value', evt.target.value)}
      >
        <Select
          value={baseline.unit}
          disabled={disabled}
          onChange={evt => onChange('baseline', 'unit', evt.target.value)}
        >
          {selectionOptions()}
        </Select>
        {baselineInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
      </LabeledInput>
      <LabeledInput
        label="Target"
        type="number"
        min="0"
        value={target.value}
        disabled={disabled}
        isInvalid={targetInvalid || tooLow}
        onChange={evt => onChange('target', 'value', evt.target.value)}
      >
        <Select
          value={target.unit}
          disabled={disabled}
          onChange={evt => onChange('target', 'unit', evt.target.value)}
        >
          {selectionOptions()}
        </Select>
        {targetInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
        {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
      </LabeledInput>
    </ControlGroup>
  );
}

function selectionOptions() {
  return (
    <>
      <Select.Option value="millis">Milliseconds</Select.Option>
      <Select.Option value="seconds">Seconds</Select.Option>
      <Select.Option value="minutes">Minutes</Select.Option>
      <Select.Option value="hours">Hours</Select.Option>
      <Select.Option value="days">Days</Select.Option>
      <Select.Option value="weeks">Weeks</Select.Option>
      <Select.Option value="months">Months</Select.Option>
      <Select.Option value="years">Years</Select.Option>
    </>
  );
}
