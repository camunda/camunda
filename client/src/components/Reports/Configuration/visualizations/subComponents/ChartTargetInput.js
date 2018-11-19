import React from 'react';

import {ButtonGroup, Button, Input, ErrorMessage, Select} from 'components';
import classnames from 'classnames';
import './ChartTargetInput.scss';
import {numberParser} from 'services';

export default function ChartTargetInput({configuration: {targetValue}, onChange, report}) {
  const definedTarget = targetValue && targetValue.active && targetValue.values;
  const invalidTarget = definedTarget && !numberParser.isPositiveNumber(targetValue.values.target);

  function setValues(prop, value) {
    onChange('targetValue', {
      ...targetValue,
      values: {
        ...(targetValue ? targetValue.values : {}),
        [prop]: value
      }
    });
  }

  return (
    <div className="ChartTargetInput">
      <ButtonGroup className="buttonGroup">
        <Button
          onClick={() => setValues('isBelow', false)}
          className={classnames({'is-active': !definedTarget || !targetValue.values.isBelow})}
        >
          Above
        </Button>
        <Button
          onClick={() => setValues('isBelow', true)}
          className={classnames({'is-active': definedTarget && targetValue.values.isBelow})}
        >
          Below
        </Button>
      </ButtonGroup>
      <Input
        className="targetInput"
        type="number"
        placeholder="Goal value"
        value={definedTarget && !invalidTarget ? targetValue.values.target : ''}
        onChange={({target: {value}}) => setValues('target', value)}
        isInvalid={invalidTarget}
      />
      {invalidTarget && (
        <ErrorMessage className="InvalidTargetError">Must be a non-negative number</ErrorMessage>
      )}
      {isDurationReport(report) && (
        <Select
          className="dataUnitSelect"
          value={targetValue ? targetValue.values.dateFormat : ''}
          onChange={({target: {value}}) => setValues('dateFormat', value)}
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

function isDurationReport(report) {
  if (report.reportType === 'single') return report.data.view.property === 'duration';
  else if (report.result && Object.values(report.result).length)
    return Object.values(report.result)[0].data.view.property === 'duration';
  else return false;
}
