/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import './DatePicker.scss';

import DateFields from './DateFields';

import {isDateValid, DATE_FORMAT} from './service';

export default class DatePicker extends React.Component {
  constructor(props) {
    super(props);

    const initialDates = this.props.initialDates || {};
    const startDate = initialDates.startDate || moment();
    const endDate = initialDates.endDate || moment();
    this.state = {
      startDate: startDate.format(DATE_FORMAT),
      endDate: endDate.format(DATE_FORMAT),
      valid: true
    };
  }

  componentDidUpdate(_, prevState) {
    const isAllValid = isDateValid(this.state.startDate) && isDateValid(this.state.endDate);
    if (
      this.props.onDateChange &&
      isAllValid &&
      (this.state.startDate !== prevState.startDate ||
        this.state.endDate !== prevState.endDate ||
        this.state.valid !== prevState.valid)
    ) {
      // will reach here if one of the buttons get clicked
      this.props.onDateChange({
        startDate: moment(this.state.startDate, DATE_FORMAT),
        endDate: moment(this.state.endDate, DATE_FORMAT),
        valid: true
      });
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
