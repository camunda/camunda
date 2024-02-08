/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Stack, TextInput} from '@carbon/react';

import {CarbonSelect, DatePicker} from 'components';
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
          <CarbonSelect
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
            <CarbonSelect.Option value="today" label={t('common.filter.dateModal.unit.today')} />
            <CarbonSelect.Option
              value="yesterday"
              label={t('common.filter.dateModal.unit.yesterday')}
            />
            <CarbonSelect.Option value="this" label={t('common.filter.dateModal.unit.this')} />
            <CarbonSelect.Option value="last" label={t('common.filter.dateModal.unit.last')} />
            <CarbonSelect.Option
              value="between"
              label={t('common.filter.dateModal.unit.between')}
            />
            <CarbonSelect.Option value="before" label={t('common.filter.dateModal.unit.before')} />
            <CarbonSelect.Option value="after" label={t('common.filter.dateModal.unit.after')} />
            <CarbonSelect.Option
              className="customDate"
              value="custom"
              label={t('common.filter.dateModal.unit.custom')}
            />
          </CarbonSelect>
          <div className="unitSelection">
            {!isFixed(type) && type !== 'custom' && (
              <CarbonSelect
                size="md"
                id={`date-range-input-${unit}-unit-selector`}
                disabled={type !== 'this' && type !== 'last'}
                onChange={(unit) => onChange({unit})}
                value={unit}
              >
                <CarbonSelect.Option value="weeks" label={t('common.unit.week.label')} />
                <CarbonSelect.Option value="months" label={t('common.unit.month.label')} />
                <CarbonSelect.Option value="years" label={t('common.unit.year.label')} />
                <CarbonSelect.Option value="quarters" label={t('common.unit.quarter.label')} />
              </CarbonSelect>
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
                <CarbonSelect
                  size="md"
                  id={`date-range-input-${unit}-unit-selector`}
                  onChange={(unit) => onChange({unit})}
                  value={unit}
                >
                  <CarbonSelect.Option
                    value="minutes"
                    label={t('common.unit.minute.label-plural')}
                  />
                  <CarbonSelect.Option value="hours" label={t('common.unit.hour.label-plural')} />
                  <CarbonSelect.Option value="days" label={t('common.unit.day.label-plural')} />
                  <CarbonSelect.Option value="weeks" label={t('common.unit.week.label-plural')} />
                  <CarbonSelect.Option value="months" label={t('common.unit.month.label-plural')} />
                  <CarbonSelect.Option value="years" label={t('common.unit.year.label-plural')} />
                </CarbonSelect>
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
