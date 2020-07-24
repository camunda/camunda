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
    const flush = () => applyChanges.flush();

    return (
      <fieldset className="NumVariableBucket">
        <legend>
          <Switch
            checked={active}
            onChange={(evt) =>
              onChange({customNumberBucket: {active: {$set: evt.target.checked}}}, true)
            }
            label={t('report.config.bucket.bucketSize')}
          />
        </legend>
        <Input
          disabled={!active}
          isInvalid={!sizeValid}
          onBlur={flush}
          onChange={(evt) => {
            const valid = numberParser.isPositiveNumber(evt.target.value);
            setSizeValid(valid);
            applyChanges('bucketSize', evt.target.value, valid);
          }}
          defaultValue={removeTrailingZeros(bucketSize)}
        />
        {!sizeValid && <Message error>{t('common.errors.postiveNum')}</Message>}
        <LabeledInput
          label={t('report.config.bucket.baseline')}
          disabled={!active}
          isInvalid={!baseValid}
          onBlur={flush}
          onChange={(evt) => {
            const valid = numberParser.isFloatNumber(evt.target.value);
            setBaseValid(valid);
            applyChanges('baseline', evt.target.value, valid);
          }}
          defaultValue={removeTrailingZeros(baseline)}
        />
        {!baseValid && <Message error>{t('report.config.bucket.invalidNumber')}</Message>}
      </fieldset>
    );
  }
  return null;
}

function removeTrailingZeros(val) {
  return val.replace(/\.0+$/, '');
}
