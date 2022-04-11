/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useMemo, useState} from 'react';

import {numberParser} from 'services';
import {Input, Select, Message} from 'components';
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
    <div className="RollingFilter">
      {t('common.filter.dateModal.last')}
      <Input
        className="number"
        value={value}
        onChange={({target: {value}}) => {
          setValue(value);
          updateValue(value);
        }}
        maxLength="8"
        isInvalid={!numberParser.isPositiveInt(filter?.start?.value)}
      />
      <Select
        value={filter?.start?.unit}
        onChange={(unit) => {
          onChange({unit});
        }}
      >
        <Select.Option value="minutes">{t('common.unit.minute.label-plural')}</Select.Option>
        <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
        <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
        <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
        <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
        <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
      </Select>
      <Message className="rollingInfo">{t('common.filter.dateModal.rollingInfo')}</Message>
      {!numberParser.isPositiveInt(value) && (
        <Message error>{t('common.errors.positiveInt')}</Message>
      )}
    </div>
  );
}
