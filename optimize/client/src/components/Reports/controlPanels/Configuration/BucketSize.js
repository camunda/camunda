/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {FormGroup, NumberInput, Stack, Toggle} from '@carbon/react';
import debounce from 'debounce';

import {Select} from 'components';
import {numberParser, formatters} from 'services';
import {t} from 'translation';

import './BucketSize.scss';

export default function BucketSize({
  configuration,
  groupBy,
  distributedBy,
  reportResult,
  onChange,
  disabled,
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
    const {active, bucketSize, baseline, bucketSizeUnit, baselineUnit} =
      configuration[customBucket];
    const flush = () => applyChanges.flush();

    const units = (
      <>
        <Select.Option value="millisecond" label={t('common.unit.milli.label-plural')} />
        <Select.Option value="second" label={t('common.unit.second.label-plural')} />
        <Select.Option value="minute" label={t('common.unit.minute.label-plural')} />
        <Select.Option value="hour" label={t('common.unit.hour.label-plural')} />
        <Select.Option value="day" label={t('common.unit.day.label-plural')} />
        <Select.Option value="week" label={t('common.unit.week.label-plural')} />
        <Select.Option value="month" label={t('common.unit.month.label-plural')} />
        <Select.Option value="year" label={t('common.unit.year.label-plural')} />
      </>
    );

    return (
      <FormGroup
        className="BucketSize"
        key={active.toString()}
        legendText={
          <Toggle
            id="bucketSizeToggle"
            size="sm"
            labelText={disabled ? t('report.updateReportPreview.cannotUpdate') : undefined}
            disabled={disabled}
            toggled={active}
            onToggle={(checked) => {
              const change = {[customBucket]: {active: {$set: checked}}};

              if (checked) {
                const values = getValues(reportResult.measures[0].data, isDistributedByVariable);
                if (values.length > 1) {
                  const bucketSize = (Math.max(...values) - Math.min(...values)) / 10;
                  const baseline = Math.min(...values);
                  if (isGroupedByDuration) {
                    const [bucketSizeDuration, bucketSizeUnit] = toDuration(bucketSize);
                    const [baselineDuration, baselineUnit] = toDuration(baseline);
                    change[customBucket] = {
                      $set: {
                        active: checked,
                        bucketSize: bucketSizeDuration,
                        bucketSizeUnit: bucketSizeUnit,
                        baseline: baselineDuration,
                        baselineUnit: baselineUnit,
                      },
                    };
                  } else {
                    change[customBucket].bucketSize = {$set: bucketSize};
                    change[customBucket].baseline = {$set: baseline};
                  }
                }
              }

              onChange(change, true);
            }}
            labelA={t('report.config.bucket.bucketSize')}
            labelB={t('report.config.bucket.bucketSize')}
          />
        }
      >
        <Stack gap={4}>
          <Stack gap={4} orientation="horizontal">
            <NumberInput
              label={t('report.config.bucket.size')}
              id="bucketSize"
              disabled={!active}
              invalid={!sizeValid}
              invalidText={t('common.errors.positiveNum')}
              onBlur={flush}
              onChange={(_evt, {value}) => {
                const valid = numberParser.isPositiveNumber(value);
                setSizeValid(valid);
                applyChanges('bucketSize', value, valid);
              }}
              defaultValue={active ? removeTrailingZeros(bucketSize) : '-'}
              hideSteppers
            />
            {isGroupedByDuration && (
              <Select
                labelText={t('common.units')}
                size="md"
                id="bucketSizeUnit"
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
          </Stack>
          <Stack gap={4} orientation="horizontal">
            <NumberInput
              id="bucketSizeBaseline"
              label={t('report.config.bucket.baseline')}
              disabled={!active}
              invalid={!baseValid}
              invalidText={t('report.config.bucket.invalidNumber')}
              onBlur={flush}
              onChange={(_evt, {value}) => {
                const valid = isGroupedByDuration
                  ? numberParser.isNonNegativeNumber(value)
                  : numberParser.isFloatNumber(value);
                setBaseValid(valid);
                applyChanges('baseline', value, valid);
              }}
              defaultValue={active ? removeTrailingZeros(baseline) : '-'}
              hideSteppers
            />
            {isGroupedByDuration && (
              <Select
                labelText={t('common.units')}
                size="md"
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
          </Stack>
        </Stack>
      </FormGroup>
    );
  }
  return null;
}

function removeTrailingZeros(val) {
  return val.toString().replace(/\.0+$/, '');
}

function getValues(data, isNested = false) {
  const values = [];
  data.forEach(({key, value}) => {
    if (isNested) {
      values.push(...getValues(value));
    } else if (key !== 'missing') {
      values.push(Number.parseFloat(key));
    }
  });
  return values;
}

const unitFormats = {
  millis: 'millisecond',
  seconds: 'second',
  minutes: 'minute',
  hours: 'hour',
  days: 'day',
  weeks: 'week',
  months: 'month',
  years: 'year',
};

function toDuration(valueMs) {
  const {value, unit} = formatters.convertDurationToObject(Math.floor(valueMs));

  return [value, unitFormats[unit]];
}
