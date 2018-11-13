import React from 'react';

import {ControlGroup, LabeledInput, ErrorMessage} from 'components';

import {isValidNumber} from './service';

import './CountTargetInput.scss';

export default function CountTargetInput({baseline, target, disabled, onChange}) {
  const baselineInvalid = !isValidNumber(baseline);
  const targetInvalid = !isValidNumber(target);

  const tooLow = !baselineInvalid && !targetInvalid && parseFloat(target) <= parseFloat(baseline);

  return (
    <ControlGroup className="CountTargetInput">
      <LabeledInput
        label="Baseline"
        value={baseline}
        disabled={disabled}
        isInvalid={baselineInvalid}
        onChange={evt => onChange('baseline', evt.target.value)}
      >
        {baselineInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
      </LabeledInput>
      <LabeledInput
        label="Target"
        value={target}
        disabled={disabled}
        isInvalid={targetInvalid || tooLow}
        onChange={evt => onChange('target', evt.target.value)}
      >
        {targetInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
        {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
      </LabeledInput>
    </ControlGroup>
  );
}
