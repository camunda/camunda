/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button, Input, Select, ErrorMessage, Message, Labeled} from 'components';
import {numberParser} from 'services';

import './DurationFilter.scss';

export default class DurationFilter extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      value: props.filterData ? props.filterData.data.value.toString() : '7',
      operator: props.filterData ? props.filterData.data.operator : '>',
      unit: props.filterData ? props.filterData.data.unit : 'days'
    };
  }

  createFilter = () => {
    this.props.addFilter({
      type: 'processInstanceDuration',
      data: {
        value: parseFloat(this.state.value),
        operator: this.state.operator,
        unit: this.state.unit
      }
    });
  };

  render() {
    const {value, operator, unit} = this.state;
    const isValidInput = numberParser.isPositiveNumber(value);
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={isValidInput ? this.createFilter : undefined}
        className="DurationFilter__modal"
      >
        <Modal.Header>Add Duration Filter</Modal.Header>
        <Modal.Content>
          <Message type="warning">
            Reports with an active Duration Filter will only show completed instances.
          </Message>
          <div className="DurationFilter__inputs">
            <Labeled label="Only include process instances that ran for">
              {' '}
              <Select
                value={operator}
                onChange={this.setOperator}
                className="DurationFilter__operator-select"
              >
                <Select.Option value=">">more</Select.Option>
                <Select.Option value="<">less</Select.Option>
              </Select>{' '}
              than
            </Labeled>
            <Input
              isInvalid={!isValidInput}
              value={value}
              onChange={this.setValue}
              className="DurationFilter__input"
            />
            <Select value={unit} onChange={this.setUnit}>
              <Select.Option value="millis">Milliseconds</Select.Option>
              <Select.Option value="seconds">Seconds</Select.Option>
              <Select.Option value="minutes">Minutes</Select.Option>
              <Select.Option value="hours">Hours</Select.Option>
              <Select.Option value="days">Days</Select.Option>
              <Select.Option value="weeks">Weeks</Select.Option>
              <Select.Option value="months">Months</Select.Option>
              <Select.Option value="years">Years</Select.Option>
            </Select>
            {!isValidInput && (
              <ErrorMessage className="DurationFilter__warning">
                Please enter a positive numeric value
              </ErrorMessage>
            )}
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            variant="primary"
            color="blue"
            disabled={!isValidInput}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  setOperator = operator => this.setState({operator});
  setUnit = unit => this.setState({unit});
  setValue = ({target: {value}}) => this.setState({value});
}
