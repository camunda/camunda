/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ButtonGroup, Button, Input, ErrorMessage, Select} from 'components';
import classnames from 'classnames';
import './ChartTargetInput.scss';
import {numberParser} from 'services';

export default function ChartTargetInput({onChange, report}) {
  const {
    configuration: {targetValue}
  } = report.data;
  const referenceReport = report.combined ? Object.values(report.result.data)[0] : report;
  const type = referenceReport.data.view.property === 'frequency' ? 'countChart' : 'durationChart';

  function setValues(prop, value) {
    onChange({
      targetValue: {
        [type]: {
          [prop]: {$set: value}
        }
      }
    });
  }

  const isInvalid = !numberParser.isNonNegativeNumber(targetValue[type].value);

  return (
    <div className="ChartTargetInput">
      <ButtonGroup className="buttonGroup" disabled={!targetValue.active}>
        <Button
          onClick={() => setValues('isBelow', false)}
          className={classnames({'is-active': !targetValue[type].isBelow})}
        >
          Above
        </Button>
        <Button
          onClick={() => setValues('isBelow', true)}
          className={classnames({'is-active': targetValue[type].isBelow})}
        >
          Below
        </Button>
      </ButtonGroup>
      <Input
        className="targetInput"
        type="number"
        min="0"
        placeholder="Goal value"
        value={targetValue[type].value}
        onChange={({target: {value}}) => setValues('value', value)}
        isInvalid={isInvalid}
        disabled={!targetValue.active}
      />
      {isInvalid && (
        <ErrorMessage className="InvalidTargetError">Must be a non-negative number</ErrorMessage>
      )}
      {type === 'durationChart' && (
        <Select
          className="dataUnitSelect"
          value={targetValue[type].unit}
          onChange={({target: {value}}) => setValues('unit', value)}
          disabled={!targetValue.active}
        >
          <Select.Option value="millis">Milliseconds</Select.Option>
          <Select.Option value="seconds">Seconds</Select.Option>
          <Select.Option value="minutes">Minutes</Select.Option>
          <Select.Option value="hours">Hours</Select.Option>
          <Select.Option value="days">Days</Select.Option>
          <Select.Option value="weeks">Weeks</Select.Option>
          <Select.Option value="months">Months</Select.Option>
          <Select.Option value="years">Years</Select.Option>
        </Select>
      )}
    </div>
  );
}
