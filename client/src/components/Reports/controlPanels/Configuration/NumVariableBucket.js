/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useCallback, useState} from 'react';

import {LabeledInput, Switch, Input, Message} from 'components';
import debounce from 'debounce';
import {numberParser} from 'services';
import {t} from 'translation';

export default function NumVariableBucket({
  report: {
    data: {
      configuration: {customNumberBucket},
      groupBy,
    },
  },
  onChange,
}) {
  const [sizeValid, setSizeValid] = useState(true);
  const [baseValid, setBaseValid] = useState(true);

  const applyChanges = useCallback(
    debounce((property, value, valid) => {
      if (valid) {
        onChange({customNumberBucket: {[property]: {$set: value}}}, true);
      }
    }, 800),
    []
  );

  if (
    groupBy?.type === 'variable' &&
    ['Integer', 'Double', 'Short', 'Long'].includes(groupBy.value?.type)
  ) {
    const {active, bucketSize, baseline} = customNumberBucket;

    return (
      <fieldset className="NumVariableBucket">
        <legend>
          <Switch
            checked={active}
            onChange={(evt) =>
              onChange({customNumberBucket: {active: {$set: evt.target.checked}}}, true)
            }
            label={t('report.config.bucketSize')}
          />
        </legend>
        <Input
          disabled={!active}
          isInvalid={!sizeValid}
          onChange={(evt) => {
            const valid = numberParser.isNonNegativeNumber(evt.target.value);
            setSizeValid(valid);
            applyChanges('bucketSize', evt.target.value, valid);
          }}
          defaultValue={bucketSize}
        />
        {!sizeValid && <Message error>{t('report.config.goal.invalidInput')}</Message>}
        <LabeledInput
          label={t('report.config.baseline')}
          disabled={!active}
          isInvalid={!baseValid}
          onChange={(evt) => {
            const valid = !isNaN(evt.target.value);
            setBaseValid(valid);
            applyChanges('baseline', evt.target.value, valid);
          }}
          defaultValue={baseline}
        />
        {!baseValid && <Message error>{t('report.config.invalidNumber')}</Message>}
      </fieldset>
    );
  }
  return null;
}
