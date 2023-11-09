/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useMemo, useState} from 'react';
import {NumberInput, Stack} from '@carbon/react';

import {numberParser} from 'services';
import {CarbonSelect} from 'components';
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
        onChange={(evt, {value}) => {
          setValue(value);
          updateValue(value);
        }}
        maxLength={8}
        invalid={!numberParser.isPositiveInt(value)}
        invalidText={t('common.errors.positiveInt')}
      />
      <CarbonSelect
        value={filter?.start?.unit}
        onChange={(unit) => {
          onChange({unit});
        }}
        helperText={t('common.filter.dateModal.rollingInfo')}
      >
        <CarbonSelect.Option value="minutes" label={t('common.unit.minute.label-plural')} />
        <CarbonSelect.Option value="hours" label={t('common.unit.hour.label-plural')} />
        <CarbonSelect.Option value="days" label={t('common.unit.day.label-plural')} />
        <CarbonSelect.Option value="weeks" label={t('common.unit.week.label-plural')} />
        <CarbonSelect.Option value="months" label={t('common.unit.month.label-plural')} />
        <CarbonSelect.Option value="years" label={t('common.unit.year.label-plural')} />
      </CarbonSelect>
    </Stack>
  );
}
