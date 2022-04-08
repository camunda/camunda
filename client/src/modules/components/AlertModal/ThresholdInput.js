/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Input, Select} from 'components';
import {t} from 'translation';

import './ThresholdInput.scss';

export default function ThresholdInput({id, value, onChange, type, isInvalid}) {
  if (type === 'duration') {
    return (
      <>
        <Input
          id={id}
          value={value.value}
          isInvalid={isInvalid}
          onChange={({target}) => onChange({...value, value: target.value})}
          maxLength="8"
        />
        <Select value={value.unit} onChange={(unit) => onChange({...value, unit})}>
          <Select.Option value="millis">{t('common.unit.milli.label-plural')}</Select.Option>
          <Select.Option value="seconds">{t('common.unit.second.label-plural')}</Select.Option>
          <Select.Option value="minutes">{t('common.unit.minute.label-plural')}</Select.Option>
          <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
          <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
          <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
          <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
        </Select>
      </>
    );
  } else {
    return (
      <div className="ThresholdInput">
        <Input
          id={id}
          value={value}
          isInvalid={isInvalid}
          onChange={({target: {value}}) => onChange(value)}
        />
        {type === 'percentage' && <span className="percentageIndicator">%</span>}
      </div>
    );
  }
}
