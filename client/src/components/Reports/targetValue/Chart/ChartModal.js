import React from 'react';

import {Modal, Button, ButtonGroup, LabeledInput, Select, ErrorMessage} from 'components';
import classnames from 'classnames';

import {isValidNumber} from '../service';
import './ChartModal.css';

export default class ChartModal extends React.Component {
  state = {
    isBelow: false,
    target: 0,
    dateFormat: ''
  };

  componentDidMount() {
    this.rerenderReport();
  }

  rerenderReport() {
    const {configuration: {targetValue}} = this.props;
    if (targetValue && targetValue.values) {
      const values = targetValue.values;
      const dateFormat = !this.isDurationReport() ? '' : values.dateFormat;
      return this.setState({
        ...values,
        dateFormat
      });
    }
    return this.setState({
      target: 0,
      isBelow: false,
      dateFormat: ''
    });
  }

  isDurationReport() {
    const report = this.props.reportResult;
    if (report.reportType === 'single') return report.data.view.property === 'duration';
    else if (report.result && Object.values(report.result).length)
      return Object.values(report.result)[0].data.view.property === 'duration';
    else return false;
  }

  componentDidUpdate(prevProps) {
    if (this.props.open !== prevProps.open && this.props.open) {
      this.rerenderReport();
    }
  }

  render() {
    const isFieldValid = isValidNumber(this.state.target);
    return (
      <Modal
        open={this.props.open}
        onClose={this.props.onClose}
        onConfirm={isFieldValid ? this.confirmModal : undefined}
        className="ChartModal__modal"
      >
        <Modal.Header>Set Target Value</Modal.Header>
        <Modal.Content>
          <ButtonGroup className="ChartModal__ButtonGroup">
            <Button
              onClick={() => this.setState({isBelow: false})}
              className={classnames({'is-active': !this.state.isBelow})}
            >
              Above
            </Button>
            <Button
              onClick={() => this.setState({isBelow: true})}
              className={classnames({'is-active': this.state.isBelow})}
            >
              Below
            </Button>
          </ButtonGroup>
          <LabeledInput
            className="ChartModal__input"
            label="target"
            value={this.state.target}
            onChange={e => this.setState({target: e.target.value})}
            isInvalid={!isFieldValid}
          >
            {!isFieldValid && (
              <ErrorMessage className="ChartModal__ErrorMessage">
                Must be a non-negative number
              </ErrorMessage>
            )}
          </LabeledInput>
          {this.isDurationReport() && (
            <Select
              className="ChartModal__select"
              value={this.state.dateFormat}
              onChange={e => this.setState({dateFormat: e.target.value})}
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
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.onClose}>Cancel</Button>
          <Button type="primary" color="blue" onClick={this.confirmModal} disabled={!isFieldValid}>
            Apply
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  confirmModal = () => {
    this.props.onConfirm({...this.state, target: parseFloat(this.state.target)});
  };
}
