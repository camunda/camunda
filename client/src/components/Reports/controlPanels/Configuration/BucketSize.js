/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useMemo, useState} from 'react';
import debounce from 'debounce';

import {LabeledInput, Switch, Input, Message, Select} from 'components';
import {numberParser} from 'services';
import {t} from 'translation';

import './BucketSize.scss';

export default function BucketSize({
  report: {
    data: {configuration, groupBy, distributedBy},
  },
  onChange,
}) {
  const isDistributedByVariable =
    distributedBy?.type === 'variable' &&
    ['Integer', 'Double', 'Short', 'Long'].includes(distributedBy.value.type);

  const customBucket = isDistributedByVariable ? 'distributeByCustomBucket' : 'customBucket';

  const [sizeValid, setSizeValid] = useState(true);
  const [baseValid, setBaseValid] = useState(true);

  const applyChanges = useMemo(
    () =>
      debounce((property, value, valid) => {
        if (valid) {
          onChange({[customBucket]: {[property]: {$set: value}}}, true);
        }
      }, 800),
    [customBucket, onChange]
  );

  const isBucketableVariableReport =
    groupBy?.type.toLowerCase().includes('variable') &&
    ['Integer', 'Double', 'Short', 'Long'].includes(groupBy.value?.type);
  const isGroupedByDuration = groupBy?.type === 'duration';

  if (isBucketableVariableReport || isGroupedByDuration || isDistributedByVariable) {
    const {active, bucketSize, baseline, bucketSizeUnit, baselineUnit} = configuration[
      customBucket
    ];
    const flush = () => applyChanges.flush();

    const units = (
      <>
        <Select.Option value="millisecond">{t('common.unit.milli.label-plural')}</Select.Option>
        <Select.Option value="second">{t('common.unit.second.label-plural')}</Select.Option>
        <Select.Option value="minute">{t('common.unit.minute.label-plural')}</Select.Option>
        <Select.Option value="hour">{t('common.unit.hour.label-plural')}</Select.Option>
        <Select.Option value="day">{t('common.unit.day.label-plural')}</Select.Option>
        <Select.Option value="week">{t('common.unit.week.label-plural')}</Select.Option>
        <Select.Option value="month">{t('common.unit.month.label-plural')}</Select.Option>
        <Select.Option value="year">{t('common.unit.year.label-plural')}</Select.Option>
      </>
    );

    return (
      <fieldset className="BucketSize">
        <legend>
          <Switch
            checked={active}
            onChange={(evt) =>
              onChange({[customBucket]: {active: {$set: evt.target.checked}}}, true)
            }
            label={t('report.config.bucket.bucketSize')}
          />
        </legend>
        <div className="inputGroup">
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
          {isGroupedByDuration && (
            <Select
              disabled={!active}
              value={bucketSizeUnit}
              onChange={(value) => {
                applyChanges('bucketSizeUnit', value, true);
                flush();
              }}
            >
              {units}
            </Select>
          )}
        </div>
        {!sizeValid && <Message error>{t('common.errors.postiveNum')}</Message>}
        <div className="inputGroup">
          <LabeledInput
            label={t('report.config.bucket.baseline')}
            disabled={!active}
            isInvalid={!baseValid}
            onBlur={flush}
            onChange={(evt) => {
              const valid = isGroupedByDuration
                ? numberParser.isNonNegativeNumber(evt.target.value)
                : numberParser.isFloatNumber(evt.target.value);
              setBaseValid(valid);
              applyChanges('baseline', evt.target.value, valid);
            }}
            defaultValue={removeTrailingZeros(baseline)}
          />
          {isGroupedByDuration && (
            <Select
              disabled={!active}
              value={baselineUnit}
              onChange={(value) => {
                applyChanges('baselineUnit', value, true);
                flush();
              }}
            >
              {units}
            </Select>
          )}
        </div>
        {!baseValid && <Message error>{t('report.config.bucket.invalidNumber')}</Message>}
      </fieldset>
    );
  }
  return null;
}

function removeTrailingZeros(val) {
  return val.replace(/\.0+$/, '');
}
