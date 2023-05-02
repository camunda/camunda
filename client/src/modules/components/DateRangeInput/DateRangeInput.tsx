/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Input, Select, Message, Form, DatePicker} from 'components';
import {t} from 'translation';
import {numberParser} from 'services';

import './DateRangeInput.scss';

interface DateRangeInputProps {
  type: string;
  unit: string;
  startDate: Date | null;
  endDate: Date | null;
  customNum: string;
  onChange: (params: {
    type?: string;
    unit?: string;
    startDate?: Date | null;
    endDate?: Date | null;
    valid?: boolean;
    customNum?: string;
  }) => void;
}

export default function DateRangeInput({
  type,
  unit,
  startDate,
  endDate,
  customNum,
  onChange,
}: DateRangeInputProps) {
  const isFixed = ['before', 'between', 'after'].includes(type);
  return (
    <Form.Group className="DateRangeInput">
      <Form.InputGroup className="selectGroup">
        <Select
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
          <Select.Option value="today">{t('common.filter.dateModal.unit.today')}</Select.Option>
          <Select.Option value="yesterday">
            {t('common.filter.dateModal.unit.yesterday')}
          </Select.Option>
          <Select.Option value="this">{t('common.filter.dateModal.unit.this')}</Select.Option>
          <Select.Option value="last">{t('common.filter.dateModal.unit.last')}</Select.Option>
          <Select.Option value="between">{t('common.filter.dateModal.unit.between')}</Select.Option>
          <Select.Option value="before">{t('common.filter.dateModal.unit.before')}</Select.Option>
          <Select.Option value="after">{t('common.filter.dateModal.unit.after')}</Select.Option>
          <Select.Option className="customDate" value="custom">
            {t('common.filter.dateModal.unit.custom')}
          </Select.Option>
        </Select>
        <div className="unitSelection">
          {!isFixed && type !== 'custom' && (
            <Select
              disabled={type !== 'this' && type !== 'last'}
              onChange={(unit) => onChange({unit})}
              value={unit}
            >
              <Select.Option value="weeks">{t('common.unit.week.label')}</Select.Option>
              <Select.Option value="months">{t('common.unit.month.label')}</Select.Option>
              <Select.Option value="years">{t('common.unit.year.label')}</Select.Option>
              <Select.Option value="quarters">{t('common.unit.quarter.label')}</Select.Option>
            </Select>
          )}
          {isFixed && (
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
              <Input
                className="number"
                value={customNum ?? ''}
                onChange={({target: {value}}) => onChange({customNum: value})}
                maxLength={8}
              />
              <Select onChange={(unit) => onChange({unit})} value={unit}>
                <Select.Option value="minutes">
                  {t('common.unit.minute.label-plural')}
                </Select.Option>
                <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
                <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
                <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
                <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
                <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
              </Select>
              {!numberParser.isPositiveInt(customNum) && (
                <Message error>{t('common.errors.positiveInt')}</Message>
              )}
            </>
          )}
        </div>
      </Form.InputGroup>
      {type === 'custom' && (
        <Message className="rollingInfo">{t('common.filter.dateModal.rollingInfo')}</Message>
      )}
    </Form.Group>
  );
}
