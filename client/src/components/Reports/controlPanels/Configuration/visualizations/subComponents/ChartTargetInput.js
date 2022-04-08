/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {ButtonGroup, Button, Input, Message, Select} from 'components';
import {numberParser} from 'services';
import {t} from 'translation';

export default function ChartTargetInput({onChange, report}) {
  const {
    configuration: {targetValue},
  } = report.data;
  const referenceReport = report.combined ? Object.values(report.result.data)[0] : report;
  const isCountReport = ['frequency', 'percentage'].includes(
    referenceReport.data.view.properties[0]
  );
  const type = isCountReport ? 'countChart' : 'durationChart';

  function setValues(prop, value) {
    onChange({
      targetValue: {
        [type]: {
          [prop]: {$set: value},
        },
      },
    });
  }

  const isInvalid = !numberParser.isNonNegativeNumber(targetValue[type].value);

  return (
    <>
      <ButtonGroup className="buttonGroup" disabled={!targetValue.active}>
        <Button onClick={() => setValues('isBelow', false)} active={!targetValue[type].isBelow}>
          {t('common.above')}
        </Button>
        <Button onClick={() => setValues('isBelow', true)} active={targetValue[type].isBelow}>
          {t('common.below')}
        </Button>
      </ButtonGroup>
      <Input
        type="number"
        min="0"
        placeholder={t('report.config.goal.goalValue')}
        value={targetValue[type].value}
        onChange={({target: {value}}) => setValues('value', value)}
        isInvalid={isInvalid}
        disabled={!targetValue.active}
      />
      {isInvalid && (
        <Message error className="InvalidTargetError">
          {t('report.config.goal.invalidInput')}
        </Message>
      )}
      {type === 'durationChart' && (
        <Select
          value={targetValue[type].unit}
          onChange={(value) => setValues('unit', value)}
          disabled={!targetValue.active}
        >
          <Select.Option value="millis">{t('common.unit.milli.label-plural')}</Select.Option>
          <Select.Option value="seconds">{t('common.unit.second.label-plural')}</Select.Option>
          <Select.Option value="minutes">{t('common.unit.minute.label-plural')}</Select.Option>
          <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
          <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
          <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
          <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
          <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
        </Select>
      )}
    </>
  );
}
