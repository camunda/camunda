/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Form, LabeledInput, ErrorMessage} from 'components';

import {numberParser} from 'services';
const {isNonNegativeNumber} = numberParser;

export default function CountTargetInput({baseline, target, disabled, onChange}) {
  const baselineInvalid = !isNonNegativeNumber(baseline);
  const targetInvalid = !isNonNegativeNumber(target);

  const tooLow = !baselineInvalid && !targetInvalid && parseFloat(target) <= parseFloat(baseline);

  return (
    <Form.InputGroup className="CountTargetInput">
      <LabeledInput
        label="Baseline"
        type="number"
        min="0"
        value={baseline}
        disabled={disabled}
        isInvalid={baselineInvalid}
        onChange={evt => onChange('baseline', evt.target.value)}
      >
        {baselineInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
      </LabeledInput>
      <LabeledInput
        label="Target"
        type="number"
        min="0"
        value={target}
        disabled={disabled}
        isInvalid={targetInvalid || tooLow}
        onChange={evt => onChange('target', evt.target.value)}
      />
      {targetInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
      {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
    </Form.InputGroup>
  );
}
