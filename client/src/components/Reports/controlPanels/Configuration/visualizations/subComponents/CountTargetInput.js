/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Button, ButtonGroup, LabeledInput, Message} from 'components';
import {numberParser} from 'services';
import {t} from 'translation';

import './CountTargetInput.scss';

const {isNonNegativeNumber} = numberParser;

export default function CountTargetInput({
  baseline,
  target,
  isBelow,
  isPercentageReport,
  disabled,
  onChange,
}) {
  const baselineInvalid = !isNonNegativeNumber(baseline);
  const targetInvalid = !isNonNegativeNumber(target);

  const tooLow = !baselineInvalid && !targetInvalid && parseFloat(target) <= parseFloat(baseline);

  return (
    <div className="CountTargetInput">
      <ButtonGroup disabled={disabled}>
        <Button onClick={() => onChange('isBelow', false)} active={!isBelow}>
          {t('common.above')}
        </Button>
        <Button onClick={() => onChange('isBelow', true)} active={isBelow}>
          {t('common.below')}
        </Button>
      </ButtonGroup>
      <div className="targetInput">
        <LabeledInput
          type="number"
          min="0"
          value={target}
          disabled={disabled}
          isInvalid={targetInvalid || tooLow}
          onChange={(evt) => onChange('target', evt.target.value)}
        />
        {isPercentageReport && <span className="percentageIndicator">%</span>}
      </div>
      <LabeledInput
        label={t('report.config.goal.baseline')}
        type="number"
        min="0"
        value={baseline}
        disabled={disabled}
        isInvalid={baselineInvalid}
        onChange={(evt) => onChange('baseline', evt.target.value)}
      />
      {(targetInvalid || baselineInvalid) && (
        <Message error>{t('report.config.goal.invalidInput')}</Message>
      )}
      {tooLow && <Message error>{t('report.config.goal.lessThanTargetError')}</Message>}
    </div>
  );
}
