import React from 'react';

import {Input, Select, Message, Form, DatePicker} from 'components';
import {t} from 'translation';
import {numberParser} from 'services';

import './DateRangeInput.scss';

export default function DateRangeInput({
  type,
  unit,
  startDate,
  endDate,
  customNum,
  disabled,
  onChange,
}) {
  return (
    <Form.Group className="DateRangeInput">
      <Form.InputGroup className="selectGroup">
        <Select
          disabled={disabled}
          onChange={(type) => onChange({type, unit: type === 'custom' ? 'days' : ''})}
          value={type}
        >
          <Select.Option value="today">{t('common.filter.dateModal.unit.today')}</Select.Option>
          <Select.Option value="yesterday">
            {t('common.filter.dateModal.unit.yesterday')}
          </Select.Option>
          <Select.Option value="this">{t('common.filter.dateModal.unit.this')}</Select.Option>
          <Select.Option value="last">{t('common.filter.dateModal.unit.last')}</Select.Option>
          <Select.Option value="fixed">{t('common.filter.dateModal.unit.fixed')}</Select.Option>
          <Select.Option className="customDate" value="custom">
            {t('common.filter.dateModal.unit.custom')}
          </Select.Option>
        </Select>
        <div className="unitSelection">
          {type !== 'fixed' && type !== 'custom' && (
            <Select
              disabled={(type !== 'this' && type !== 'last') || disabled}
              onChange={(unit) => onChange({unit})}
              value={unit}
            >
              <Select.Option value="weeks">{t('common.unit.week.label')}</Select.Option>
              <Select.Option value="months">{t('common.unit.month.label')}</Select.Option>
              <Select.Option value="years">{t('common.unit.year.label')}</Select.Option>
              <Select.Option value="quarters">{t('common.unit.quarter.label')}</Select.Option>
            </Select>
          )}
          {type === 'fixed' && (
            <DatePicker
              onDateChange={onChange}
              initialDates={{
                startDate,
                endDate,
              }}
              disabled={disabled}
            />
          )}
          {type === 'custom' && (
            <>
              {t('common.filter.dateModal.last')}
              <Input
                className="number"
                value={customNum ?? ''}
                onChange={({target: {value}}) => onChange({customNum: value})}
                maxLength="8"
                disabled={disabled}
              />
              <Select onChange={(unit) => onChange({unit})} value={unit} disabled={disabled}>
                <Select.Option value="minutes">
                  {t('common.unit.minute.label-plural')}
                </Select.Option>
                <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
                <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
                <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
                <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
                <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
              </Select>
              {!numberParser.isPostiveInt(customNum) && (
                <Message error>{t('common.filter.dateModal.invalidInput')}</Message>
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
