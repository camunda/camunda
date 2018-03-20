import React from 'react';

import {Input, Select} from 'components';

export default function ThresholdInput({id, value, onChange, type, isInvalid}) {
  if (type === 'duration') {
    return (
      <div className="AlertModal__combinedInput">
        <Input
          id={id}
          className="AlertModal__input"
          value={value.value}
          isInvalid={isInvalid}
          onChange={({target}) => onChange({...value, value: target.value})}
        />
        <Select
          value={value.unit}
          onChange={({target}) => onChange({...value, unit: target.value})}
        >
          <Select.Option value="milliseconds">Milliseconds</Select.Option>
          <Select.Option value="seconds">Seconds</Select.Option>
          <Select.Option value="minutes">Minutes</Select.Option>
          <Select.Option value="hours">Hours</Select.Option>
          <Select.Option value="days">Days</Select.Option>
          <Select.Option value="weeks">Weeks</Select.Option>
          <Select.Option value="months">Months</Select.Option>
        </Select>
      </div>
    );
  } else {
    return (
      <Input
        id={id}
        className="AlertModal__input"
        value={value}
        isInvalid={isInvalid}
        onChange={({target: {value}}) => onChange(value)}
      />
    );
  }
}
