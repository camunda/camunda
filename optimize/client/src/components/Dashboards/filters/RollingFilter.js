/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {NumberInput, Stack} from '@carbon/react';

import {numberParser} from 'services';
import {Select} from 'components';
import {t} from 'translation';

import './RollingFilter.scss';
import debounce from 'debounce';

export default function RollingFilter({filter, onChange}) {
  const [value, setValue] = useState(filter?.start?.value);
  const updateValue = useMemo(
    () =>
      debounce((value) => {
        if (!value || numberParser.isPositiveInt(value)) {
          onChange({value: Number(value)});
        }
      }, 500),
    [onChange]
  );

  useEffect(() => {
    return () => {
      updateValue.clear();
    };
  }, [updateValue]);

  return (
    <Stack gap={4} className="RollingFilter">
      <NumberInput
        label={t('common.filter.dateModal.last')}
        id="numberOfLast"
        size="sm"
        className="number"
        value={value}
        onChange={(_evt, {value}) => {
          setValue(value);
          updateValue(value);
        }}
        maxLength={8}
        invalid={!numberParser.isPositiveInt(value)}
        invalidText={t('common.errors.positiveInt')}
      />
      <Select
        value={filter?.start?.unit}
        onChange={(unit) => {
          onChange({unit});
        }}
        helperText={t('common.filter.dateModal.rollingInfo')}
      >
        <Select.Option value="minutes" label={t('common.unit.minute.label-plural')} />
        <Select.Option value="hours" label={t('common.unit.hour.label-plural')} />
        <Select.Option value="days" label={t('common.unit.day.label-plural')} />
        <Select.Option value="weeks" label={t('common.unit.week.label-plural')} />
        <Select.Option value="months" label={t('common.unit.month.label-plural')} />
        <Select.Option value="years" label={t('common.unit.year.label-plural')} />
      </Select>
    </Stack>
  );
}
