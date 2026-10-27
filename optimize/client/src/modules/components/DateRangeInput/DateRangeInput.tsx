/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack, TextInput} from '@carbon/react';

import {Select, DatePicker} from 'components';
import {t} from 'translation';
import {numberParser} from 'services';

import './DateRangeInput.scss';

interface DateRangeInputProps {
  type: string;
  unit: string;
  startDate: Date | null;
  endDate: Date | null;
  customNum: string;
  onChange: (
    params: Partial<{
      type: string;
      unit: string;
      startDate: Date | null;
      endDate: Date | null;
      valid: boolean;
      customNum: string;
    }>
  ) => void;
}

export default function DateRangeInput({
  type,
  unit,
  startDate,
  endDate,
  customNum,
  onChange,
}: DateRangeInputProps) {
  const isFixed = (type: string): type is 'before' | 'after' | 'between' =>
    ['before', 'between', 'after'].includes(type);
  return (
    <div className="DateRangeInput">
      <Stack gap={6}>
        <Stack gap={4} orientation="horizontal" className="selectGroup">
          <Select
            size="md"
            id={`date-range-input-${type}-type-selector`}
            onChange={(type) =>
              onChange({
                type,
                unit: type === 'custom' ? 'days' : '',
                startDate: null,
                endDate: null,
                valid: false,
              })
            }
            value={type}
          >
            <Select.Option value="today" label={t('common.filter.dateModal.unit.today')} />
            <Select.Option value="yesterday" label={t('common.filter.dateModal.unit.yesterday')} />
            <Select.Option value="this" label={t('common.filter.dateModal.unit.this')} />
            <Select.Option value="last" label={t('common.filter.dateModal.unit.last')} />
            <Select.Option value="between" label={t('common.filter.dateModal.unit.between')} />
            <Select.Option value="before" label={t('common.filter.dateModal.unit.before')} />
            <Select.Option value="after" label={t('common.filter.dateModal.unit.after')} />
            <Select.Option
              className="customDate"
              value="custom"
              label={t('common.filter.dateModal.unit.custom')}
            />
          </Select>
          <div className="unitSelection">
            {!isFixed(type) && type !== 'custom' && (
              <Select
                size="md"
                id={`date-range-input-${unit}-unit-selector`}
                disabled={type !== 'this' && type !== 'last'}
                onChange={(unit) => onChange({unit})}
                value={unit}
              >
                <Select.Option value="weeks" label={t('common.unit.week.label')} />
                <Select.Option value="months" label={t('common.unit.month.label')} />
                <Select.Option value="years" label={t('common.unit.year.label')} />
                <Select.Option value="quarters" label={t('common.unit.quarter.label')} />
              </Select>
            )}
            {isFixed(type) && (
              <DatePicker
                key={type}
                type={type}
                onDateChange={onChange}
                initialDates={{
                  startDate,
                  endDate,
                }}
              />
            )}
            {type === 'custom' && (
              <>
                {t('common.filter.dateModal.last')}
                <TextInput
                  size="md"
                  id={`date-range-input-${unit}-custom-value-input`}
                  className="number"
                  value={customNum ?? ''}
                  onChange={({target: {value}}) => onChange({customNum: value})}
                  maxLength={8}
                  labelText={t('common.value')}
                  hideLabel
                  invalid={!!customNum && !numberParser.isPositiveInt(customNum)}
                  invalidText={t('common.errors.positiveInt')}
                />
                <Select
                  size="md"
                  id={`date-range-input-${unit}-unit-selector`}
                  onChange={(unit) => onChange({unit})}
                  value={unit}
                >
                  <Select.Option value="minutes" label={t('common.unit.minute.label-plural')} />
                  <Select.Option value="hours" label={t('common.unit.hour.label-plural')} />
                  <Select.Option value="days" label={t('common.unit.day.label-plural')} />
                  <Select.Option value="weeks" label={t('common.unit.week.label-plural')} />
                  <Select.Option value="months" label={t('common.unit.month.label-plural')} />
                  <Select.Option value="years" label={t('common.unit.year.label-plural')} />
                </Select>
              </>
            )}
          </div>
        </Stack>
        {type === 'custom' && (
          <div className="cds--form__helper-text">{t('common.filter.dateModal.rollingInfo')}</div>
        )}
      </Stack>
    </div>
  );
}
