/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {
  Modal,
  Button,
  ButtonGroup,
  Input,
  Select,
  ErrorMessage,
  DatePicker,
  Message,
  Labeled
} from 'components';
import {formatters} from 'services';
import './DateFilter.scss';

export default class DateFilter extends React.Component {
  constructor(props) {
    super(props);

    let startDate, endDate, mode, dynamicValue, dynamicUnit;

    if (props.filterData) {
      if (props.filterData.data.type === 'fixed') {
        startDate = moment(props.filterData.data.start);
        endDate = moment(props.filterData.data.end);
      } else {
        mode = 'dynamic';
        dynamicValue = props.filterData.data.start.value;
        dynamicUnit = props.filterData.data.start.unit;
      }
    }

    this.state = {
      startDate: startDate || moment(),
      endDate: endDate || moment(),
      validDate: true,
      mode: mode || 'static',
      dynamicValue: dynamicValue || '7',
      dynamicUnit: dynamicUnit || 'days'
    };
  }

  createFilter = () => {
    if (this.state.mode === 'static') {
      this.props.addFilter({
        type: this.props.filterType,
        data: {
          type: 'fixed',
          start: this.state.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
          end: this.state.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
        }
      });
    } else if (this.state.mode === 'dynamic') {
      this.props.addFilter({
        type: this.props.filterType,
        data: {
          type: 'relative',
          start: {
            value: parseFloat(this.state.dynamicValue),
            unit: this.state.dynamicUnit
          },
          end: null
        }
      });
    }
  };

  setMode = mode => {
    const newState = {
      mode,
      validDate: true
    };

    if (mode === 'static') {
      newState.startDate = moment().subtract(this.state.dynamicValue, this.state.dynamicUnit);
      newState.endDate = moment();
    } else if (mode === 'dynamic') {
      const {startDate, endDate} = this.state;
      if (startDate.isValid() && endDate.isValid()) {
        newState.dynamicUnit = 'days';
        newState.dynamicValue = Math.round(
          moment.duration(endDate.endOf('day').diff(startDate.startOf('day'))).asDays()
        );
      }
    }

    this.setState(newState);
  };

  render() {
    const {mode, validDate, startDate, endDate, dynamicValue, dynamicUnit} = this.state;
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={mode === 'dynamic' && validDate ? this.createFilter : undefined}
        className="DateFilter__modal"
      >
        <Modal.Header>{`Add ${formatters.camelCaseToLabel(
          this.props.filterType
        )} Filter`}</Modal.Header>
        <Modal.Content>
          {this.props.filterType === 'endDate' && (
            <Message type="warning">
              Reports with an active End Date Filter will only show completed instances.
            </Message>
          )}
          <ButtonGroup className="DateFilter__mode-buttons">
            <Button
              onClick={() => this.setMode('static')}
              name="button-static"
              active={mode === 'static'}
            >
              Fixed Date
            </Button>
            <Button
              onClick={() => this.setMode('dynamic')}
              name="button-dynamic"
              active={mode === 'dynamic'}
            >
              Relative Date
            </Button>
          </ButtonGroup>
          {mode === 'static' && (
            <React.Fragment>
              <Labeled label="Select start and end dates to filter by:">
                <DatePicker onDateChange={this.onDateChange} initialDates={{startDate, endDate}} />
              </Labeled>
            </React.Fragment>
          )}
          {mode === 'dynamic' && (
            <div className="DateFilter__inputs">
              <Labeled
                label={`Only include process instances ${
                  this.props.filterType === 'startDate' ? 'started' : 'ended'
                } within the last`}
              >
                <Input
                  value={dynamicValue}
                  onChange={this.setDynamicValue}
                  className="DateFilter__rolling-input"
                  isInvalid={!validDate}
                />

                <Select value={dynamicUnit} onChange={this.setDynamicUnit}>
                  <Select.Option value="minutes">Minutes</Select.Option>
                  <Select.Option value="hours">Hours</Select.Option>
                  <Select.Option value="days">Days</Select.Option>
                  <Select.Option value="weeks">Weeks</Select.Option>
                  <Select.Option value="months">Months</Select.Option>
                  <Select.Option value="years">Years</Select.Option>
                </Select>
                {!validDate && (
                  <ErrorMessage className="DateFilter__warning">
                    Please enter a numeric value
                  </ErrorMessage>
                )}
              </Labeled>
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button type="primary" color="blue" disabled={!validDate} onClick={this.createFilter}>
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  setDynamicUnit = value => this.setState({dynamicUnit: value});
  setDynamicValue = ({target: {value}}) => {
    this.setState({
      dynamicValue: value,
      validDate: value.trim() && !isNaN(value.trim()) && +value > 0
    });
  };

  onDateChange = ({startDate, endDate, valid}) => {
    this.setState({startDate, endDate, validDate: valid});
  };
}
