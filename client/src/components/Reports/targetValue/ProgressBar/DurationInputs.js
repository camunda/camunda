import React from 'react';

import {LabeledInput, ErrorMessage, Select} from 'components';

import {formatters, isDurationValue} from 'services';
import './DurationInputs.scss';

const {convertDurationToSingleNumber} = formatters;

export default class DurationInputs extends React.Component {
  state = {
    tooLow: false
  };

  static sanitizeData = ({target, baseline}) => {
    return {
      target: {unit: target.unit, value: parseFloat(target.value)},
      baseline: {unit: baseline.unit, value: parseFloat(baseline.value)}
    };
  };

  componentDidMount() {
    if (this.props.configuration.targetValue && this.props.configuration.targetValue.values) {
      return this.props.setData({...this.props.configuration.targetValue.values});
    }
    return this.props.setData({
      target: {
        value: 2,
        unit: 'hours'
      },
      baseline: {
        value: 0,
        unit: 'hours'
      }
    });
  }

  isValid = value => {
    if (typeof value === 'undefined') {
      return false;
    }
    if (typeof value === 'number') {
      return true;
    }
    if (typeof value === 'string') {
      return value.trim() && !isNaN(value.trim()) && +value >= 0;
    }
    if (isDurationValue(value)) {
      return this.isValid(value.value);
    }
  };

  isTooLow = (baseline, target) => {
    if (!this.isValid(baseline) || !this.isValid(target)) {
      return false;
    }
    if (isDurationValue(target)) {
      return convertDurationToSingleNumber(target) <= convertDurationToSingleNumber(baseline);
    }
  };

  change = (type, subType) => ({target: {value}}) => {
    const newData = {
      ...this.props.data,
      [type]: {
        ...this.props.data[type],
        [subType]: value
      }
    };
    this.props.setData(newData);

    const tooLow = this.isTooLow(newData.baseline, newData.target);
    this.setState({tooLow});

    if (subType === 'value') {
      const isValid = this.isValid(value);
      this.props.setValid(isValid);
    }
  };

  render() {
    return (
      <React.Fragment>
        {this.renderInput('baseline', 'Baseline')}
        {this.renderInput('target', 'Target Value')}
      </React.Fragment>
    );
  }

  renderInput = (stateVar, label) => {
    if (!this.props.data[stateVar]) {
      return null;
    }

    const isInvalid = !this.isValid(this.props.data[stateVar]);
    const tooLow = stateVar === 'target' && this.state.tooLow;

    return (
      <React.Fragment>
        <LabeledInput
          className="DurationInputs__input"
          label={label}
          value={this.props.data[stateVar].value}
          onChange={this.change(stateVar, 'value')}
          isInvalid={isInvalid || tooLow}
        >
          <Select value={this.props.data[stateVar].unit} onChange={this.change(stateVar, 'unit')}>
            <Select.Option value="millis">Milliseconds</Select.Option>
            <Select.Option value="seconds">Seconds</Select.Option>
            <Select.Option value="minutes">Minutes</Select.Option>
            <Select.Option value="hours">Hours</Select.Option>
            <Select.Option value="days">Days</Select.Option>
            <Select.Option value="weeks">Weeks</Select.Option>
            <Select.Option value="months">Months</Select.Option>
            <Select.Option value="years">Years</Select.Option>
          </Select>
          {isInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
          {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
        </LabeledInput>
      </React.Fragment>
    );
  };
}
