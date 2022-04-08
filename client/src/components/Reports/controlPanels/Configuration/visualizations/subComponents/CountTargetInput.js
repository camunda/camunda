/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Form, LabeledInput, Message} from 'components';

import {numberParser} from 'services';
import {t} from 'translation';
const {isNonNegativeNumber} = numberParser;

export default function CountTargetInput({baseline, target, disabled, onChange}) {
  const baselineInvalid = !isNonNegativeNumber(baseline);
  const targetInvalid = !isNonNegativeNumber(target);

  const tooLow = !baselineInvalid && !targetInvalid && parseFloat(target) <= parseFloat(baseline);

  return (
    <>
      <Form.InputGroup className="CountTargetInput">
        <LabeledInput
          label={t('report.config.goal.baseline')}
          type="number"
          min="0"
          value={baseline}
          disabled={disabled}
          isInvalid={baselineInvalid}
          onChange={(evt) => onChange('baseline', evt.target.value)}
        />
        <LabeledInput
          label={t('report.config.goal.target')}
          type="number"
          min="0"
          value={target}
          disabled={disabled}
          isInvalid={targetInvalid || tooLow}
          onChange={(evt) => onChange('target', evt.target.value)}
        />
      </Form.InputGroup>
      {(targetInvalid || baselineInvalid) && (
        <Message error>{t('report.config.goal.invalidInput')}</Message>
      )}
      {tooLow && <Message error>{t('report.config.goal.lessThanTargetError')}</Message>}
    </>
  );
}
