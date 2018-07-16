import React from 'react';
import moment from 'moment';

import {Modal, Button, ButtonGroup, Input, Select, ErrorMessage, DatePicker} from 'components';

import './DateFilter.css';

export default class DateFilter extends React.Component {
  constructor(props) {
    super(props);

    let startDate, endDate, mode, dynamicValue, dynamicUnit;

    if (props.filterData) {
      if (props.filterData[0].type === 'date') {
        startDate = moment(props.filterData[0].data.value);
        endDate = moment(props.filterData[1].data.value);
      } else {
        mode = 'dynamic';
        dynamicValue = props.filterData[0].data.value;
        dynamicUnit = props.filterData[0].data.unit;
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
      this.props.addFilter(
        {
          type: 'date',
          data: {
            type: 'start_date',
            operator: '>=',
            value: this.state.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss')
          }
        },
        {
          type: 'date',
          data: {
            type: 'start_date',
            operator: '<=',
            value: this.state.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
          }
        }
      );
    } else if (this.state.mode === 'dynamic') {
      this.props.addFilter({
        type: 'rollingDate',
        data: {
          value: parseFloat(this.state.dynamicValue),
          unit: this.state.dynamicUnit
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
    return (
      <Modal open={true} onClose={this.props.close} className="DateFilter__modal">
        <Modal.Header>Add Start Date Filter</Modal.Header>
        <Modal.Content>
          <ButtonGroup className="DateFilter__mode-buttons">
            <Button
              onClick={() => this.setMode('static')}
              name="button-static"
              active={this.state.mode === 'static'}
            >
              Fixed Date
            </Button>
            <Button
              onClick={() => this.setMode('dynamic')}
              name="button-dynamic"
              active={this.state.mode === 'dynamic'}
            >
              Relative Date
            </Button>
          </ButtonGroup>
          {this.state.mode === 'static' && (
            <React.Fragment>
              <label className="DateFilter__input-label">
                Select start and end dates to filter by:
              </label>
              <DatePicker
                onDateChange={this.onDateChange}
                initialDates={{startDate: this.state.startDate, endDate: this.state.endDate}}
              />
            </React.Fragment>
          )}
          {this.state.mode === 'dynamic' && (
            <div className="DateFilter__inputs">
              <label className="DateFilter__input-label">
                Only include process instances started within the last
              </label>
              <Input
                value={this.state.dynamicValue}
                onChange={this.setDynamicValue}
                className="DateFilter__rolling-input"
                isInvalid={!this.state.validDate}
              />
              <Select value={this.state.dynamicUnit} onChange={this.setDynamicUnit}>
                <Select.Option value="minutes">Minutes</Select.Option>
                <Select.Option value="hours">Hours</Select.Option>
                <Select.Option value="days">Days</Select.Option>
                <Select.Option value="weeks">Weeks</Select.Option>
                <Select.Option value="months">Months</Select.Option>
                <Select.Option value="years">Years</Select.Option>
              </Select>
              {!this.state.validDate && (
                <ErrorMessage className="DateFilter__warning">
                  Please enter a numeric value
                </ErrorMessage>
              )}
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            disabled={!this.state.validDate}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  setDynamicUnit = ({target: {value}}) => this.setState({dynamicUnit: value});
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
