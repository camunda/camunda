import React from 'react';

import {Modal, Button, LabeledInput, Select, ErrorMessage} from 'components';

import './NumberTargetValueModal.css';
import {formatters} from 'services';

const {convertDurationToSingleNumber} = formatters;

export default class NumberTargetValueModal extends React.Component {
  state = {};

  static getDerivedStateFromProps(nextProps) {
    if (nextProps.configuration.targetValue.values) {
      return {...nextProps.configuration.targetValue.values};
    }
    if (nextProps.reportResult.data.view.property === 'frequency') {
      return {
        target: 100,
        baseline: 0
      };
    } else if (nextProps.reportResult.data.view.property === 'duration') {
      return {
        target: {
          value: 2,
          unit: 'hours'
        },
        baseline: {
          value: 0,
          unit: 'hours'
        }
      };
    }

    return null;
  }

  isOfType = type => this.props.reportResult.data.view.property === type;

  isValid = value => {
    if (typeof value === 'number') {
      return true;
    }
    if (typeof value === 'string') {
      return value.trim() && !isNaN(value.trim()) && +value >= 0;
    }
    if (typeof value === 'object') {
      return this.isValid(value.value);
    }
  };

  targetTooLow = () => {
    if (!this.isValid(this.state.baseline) || !this.isValid(this.state.target)) {
      return false;
    }
    if (typeof this.state.target === 'object') {
      return (
        convertDurationToSingleNumber(this.state.target) <=
        convertDurationToSingleNumber(this.state.baseline)
      );
    } else {
      return parseFloat(this.state.target) <= parseFloat(this.state.baseline);
    }
  };

  render() {
    return (
      <Modal
        open={this.props.open}
        onClose={this.props.onClose}
        className="NumberTargetValueModal__modal"
      >
        <Modal.Header>Set Target Value</Modal.Header>
        <Modal.Content>
          <div className="NumberTargetValueModal__inputs">
            {this.isOfType('frequency')
              ? this.renderFrequencyInput('baseline', 'Baseline')
              : this.renderDurationInput('baseline', 'Baseline')}
            {this.isOfType('frequency')
              ? this.renderFrequencyInput('target', 'Target Value')
              : this.renderDurationInput('target', 'Target Value')}
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.onClose}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            onClick={this.confirmModal}
            disabled={
              !this.isValid(this.state.baseline) ||
              !this.isValid(this.state.target) ||
              this.targetTooLow()
            }
          >
            Apply
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  renderFrequencyInput = (stateVar, label) => {
    const isInvalid = !this.isValid(this.state[stateVar]);
    const tooLow = stateVar === 'target' && this.targetTooLow();

    return (
      <LabeledInput
        className="NumberTargetValueModal__input"
        label={label}
        value={this.state[stateVar]}
        onChange={({target: {value}}) => this.setState({[stateVar]: value})}
        isInvalid={isInvalid || tooLow}
      >
        {isInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
        {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
      </LabeledInput>
    );
  };

  renderDurationInput = (stateVar, label) => {
    const isInvalid = !this.isValid(this.state[stateVar]);
    const tooLow = stateVar === 'target' && this.targetTooLow();

    return (
      <React.Fragment>
        <LabeledInput
          className="NumberTargetValueModal__input"
          label={label}
          value={this.state[stateVar].value}
          onChange={({target: {value}}) =>
            this.setState({[stateVar]: {...this.state[stateVar], value}})
          }
          isInvalid={isInvalid || tooLow}
        >
          <Select
            value={this.state[stateVar].unit}
            onChange={({target: {value}}) =>
              this.setState({[stateVar]: {...this.state[stateVar], unit: value}})
            }
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
          {isInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
          {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
        </LabeledInput>
      </React.Fragment>
    );
  };

  confirmModal = () => {
    if (this.isOfType('frequency')) {
      this.props.onConfirm({
        target: parseFloat(this.state.target),
        baseline: parseFloat(this.state.baseline)
      });
    } else {
      this.props.onConfirm({
        target: {unit: this.state.target.unit, value: parseFloat(this.state.target.value)},
        baseline: {unit: this.state.baseline.unit, value: parseFloat(this.state.baseline.value)}
      });
    }
  };
}
