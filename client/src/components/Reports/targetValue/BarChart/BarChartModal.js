import React from 'react';

import {Modal, Button, ButtonGroup, LabeledInput, Select, ErrorMessage} from 'components';
import classnames from 'classnames';

import {isValidNumber} from '../service';
import './BarChartModal.css';

export default class BarChartModal extends React.Component {
  state = {
    data: {
      isAbove: true,
      target: 0,
      dateFormat: ''
    },
    viewProperty: ''
  };

  componentDidMount() {
    this.rerenderReport();
  }

  rerenderReport() {
    const {configuration: {targetValue: {values}}, reportResult} = this.props;
    if (values) {
      const dateFormat = reportResult.data.view.property !== 'duration' ? '' : values.dateFormat;
      return this.setState({
        data: {...this.state.data, ...values, dateFormat},
        viewProperty: reportResult.data.view.property
      });
    }
    return this.setState({
      data: {
        ...this.state.data,
        target: 0,
        dateFormat: ''
      },
      viewProperty: reportResult.data.view.property
    });
  }

  componentWillReceiveProps({open, configuration, reportResult}) {
    const {data: {target, isAbove, dateFormat}, viewProperty} = this.state;
    const {values} = configuration.targetValue;
    if (
      !values ||
      target !== values.target ||
      isAbove !== values.isAbove ||
      dateFormat !== values.dateFormat ||
      viewProperty !== reportResult.data.view.property
    )
      return this.rerenderReport();
  }

  render() {
    const isFieldValid = isValidNumber(this.state.data.target);
    return (
      <Modal open={this.props.open} onClose={this.props.onClose} className="BarChartModal__modal">
        <Modal.Header>Set Target Value</Modal.Header>
        <Modal.Content>
          <ButtonGroup className="BarChartModal__ButtonGroup">
            <Button
              onClick={() => this.setState({data: {...this.state.data, isAbove: true}})}
              className={classnames({'is-active': this.state.data.isAbove})}
            >
              Above
            </Button>
            <Button
              onClick={() => this.setState({data: {...this.state.data, isAbove: false}})}
              className={classnames({'is-active': !this.state.data.isAbove})}
            >
              Below
            </Button>
          </ButtonGroup>
          <LabeledInput
            className="BarChartModal__input"
            label="target"
            value={this.state.data.target}
            onChange={e => this.setState({data: {...this.state.data, target: e.target.value}})}
            isInvalid={!isFieldValid}
          >
            {!isFieldValid && (
              <ErrorMessage className="BarChartModal__ErrorMessage">
                Must be a non-negative number
              </ErrorMessage>
            )}
          </LabeledInput>
          {this.state.viewProperty === 'duration' && (
            <Select
              className="BarChartModal__select"
              value={this.state.data.dateFormat}
              onChange={e =>
                this.setState({data: {...this.state.data, dateFormat: e.target.value}})
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
    this.props.onConfirm({...this.state.data, target: parseFloat(this.state.data.target)});
  };
}
