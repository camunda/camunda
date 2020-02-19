/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import DateFields from './DateFields';

import {isDateValid, DATE_FORMAT} from './service';

export default class DatePicker extends React.Component {
  constructor(props) {
    super(props);

    const {startDate, endDate} = props.initialDates || {};
    if (startDate && endDate) {
      this.state = {
        startDate: startDate.format(DATE_FORMAT),
        endDate: endDate.format(DATE_FORMAT),
        valid: true
      };
    } else {
      this.state = {
        startDate: '',
        endDate: '',
        valid: false
      };
    }
  }

  setValidState = valid => this.setState({valid});

  setDates = dates => this.setState({...dates});

  onDateChange = (name, date) => {
    const dateObj = moment(date, DATE_FORMAT);

    this.setState(
      {
        [name]: date
      },
      () => {
        const isAllValid = isDateValid(this.state.startDate) && isDateValid(this.state.endDate);
        this.setState({valid: isAllValid});

        this.props.onDateChange({
          startDate: moment(this.state.startDate, DATE_FORMAT),
          endDate: moment(this.state.endDate, DATE_FORMAT),
          valid: isAllValid
        });

        if (
          isAllValid &&
          ((name === 'startDate' && dateObj.isAfter(moment(this.state.endDate, DATE_FORMAT))) ||
            (name === 'endDate' && dateObj.isBefore(moment(this.state.startDate, DATE_FORMAT))))
        ) {
          return this.setState({
            startDate: date,
            endDate: date
          });
        }
      }
    );
  };

  render() {
    return (
      <DateFields
        format={DATE_FORMAT}
        onDateChange={this.onDateChange}
        startDate={this.state.startDate}
        endDate={this.state.endDate}
        setValidState={this.setValidState}
        disabled={this.props.disabled}
      />
    );
  }
}
