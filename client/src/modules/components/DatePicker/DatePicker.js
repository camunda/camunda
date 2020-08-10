/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {parseISO, isAfter, isBefore} from 'date-fns';

import {format} from 'dates';

import DateFields from './DateFields';
import {isDateValid, DATE_FORMAT} from './service';

export default class DatePicker extends React.Component {
  constructor(props) {
    super(props);

    const {startDate, endDate} = props.initialDates || {};
    if (startDate && endDate) {
      this.state = {
        startDate: format(startDate, DATE_FORMAT),
        endDate: format(endDate, DATE_FORMAT),
        valid: true,
      };
    } else {
      this.state = {
        startDate: '',
        endDate: '',
        valid: false,
      };
    }
  }

  setValidState = (valid) => this.setState({valid});

  setDates = (dates) => this.setState({...dates});

  onDateChange = (name, date) => {
    const dateObj = parseISO(date);

    this.setState(
      {
        [name]: date,
      },
      () => {
        const isAllValid = isDateValid(this.state.startDate) && isDateValid(this.state.endDate);
        this.setState({valid: isAllValid});
        const startDate = parseISO(this.state.startDate);
        const endDate = parseISO(this.state.endDate);

        this.props.onDateChange({
          startDate,
          endDate,
          valid: isAllValid,
        });

        if (
          isAllValid &&
          ((name === 'startDate' && isAfter(dateObj, endDate)) ||
            (name === 'endDate' && isBefore(dateObj, startDate)))
        ) {
          return this.setState({
            startDate: date,
            endDate: date,
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
        forceOpen={this.props.forceOpen}
      />
    );
  }
}
