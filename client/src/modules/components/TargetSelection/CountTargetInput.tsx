/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {RadioButton, RadioButtonGroup, Stack, TextInput} from '@carbon/react';
import {Percentage} from '@carbon/icons-react';

import {numberParser} from 'services';
import {t} from 'translation';

import './CountTargetInput.scss';

interface CountTargetInputProps {
  baseline: string | number;
  target: string | number;
  isBelow?: boolean;
  isPercentageReport?: boolean;
  disabled?: boolean;
  onChange: (property: string, value: boolean | string) => void;
}

const {isNonNegativeNumber} = numberParser;

export default function CountTargetInput({
  baseline,
  target,
  isBelow,
  isPercentageReport,
  disabled,
  onChange,
}: CountTargetInputProps) {
  const baselineInvalid = !isNonNegativeNumber(baseline);
  const targetInvalid = !isNonNegativeNumber(target);

  const tooLow =
    !baselineInvalid &&
    !targetInvalid &&
    parseFloat(target.toString()) <= parseFloat(baseline.toString());

  return (
    <Stack gap={4} className="CountTargetInput">
      <RadioButtonGroup name="countTargetRadio" disabled={disabled}>
        <RadioButton
          value="false"
          checked={!isBelow}
          labelText={t('common.above')}
          onClick={() => onChange('isBelow', false)}
        />
        <RadioButton
          value="true"
          checked={isBelow}
          labelText={t('common.below')}
          onClick={() => onChange('isBelow', true)}
        />
      </RadioButtonGroup>
      <div className="targetInput">
        <TextInput
          id="setTargetInput"
          labelText={t('report.config.goal.target')}
          type="number"
          min="0"
          value={target}
          disabled={disabled}
          invalid={targetInvalid || tooLow}
          invalidText={
            tooLow
              ? t('report.config.goal.lessThanTargetError')
              : t('report.config.goal.invalidInput')
          }
          onChange={(evt) => onChange('target', evt.target.value)}
        />
        {isPercentageReport && (
          <Percentage aria-disabled={disabled} className="percentageIndicator" />
        )}
      </div>
      <TextInput
        id="setBaselineInput"
        labelText={t('report.config.goal.baseline')}
        type="number"
        min="0"
        value={baseline}
        disabled={disabled}
        invalid={baselineInvalid}
        invalidText={t('report.config.goal.invalidInput')}
        onChange={(evt) => onChange('baseline', evt.target.value)}
      />
    </Stack>
  );
}
