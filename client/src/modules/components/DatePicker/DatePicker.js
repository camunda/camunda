/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    this.state = {
      startDate: startDate ? format(startDate, DATE_FORMAT) : '',
      endDate: endDate ? format(endDate, DATE_FORMAT) : '',
    };
  }

  setDates = (dates) => this.setState({...dates});

  onDateChange = (name, date) => {
    const {type} = this.props;
    const dateObj = parseISO(date);

    this.setState(
      {
        [name]: date,
      },
      () => {
        const startDate = type !== 'before' ? parseISO(this.state.startDate) : null;
        const endDate = type !== 'after' ? parseISO(this.state.endDate) : null;
        const isAllValid = isValid(type, this.state.startDate, this.state.endDate);
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
        forceOpen={this.props.forceOpen}
        type={this.props.type}
      />
    );
  }
}

function isValid(type, startDate, endDate) {
  if (type === 'between') {
    return isDateValid(startDate) && isDateValid(endDate);
  } else if (type === 'after') {
    return isDateValid(startDate);
  } else if (type === 'before') {
    return isDateValid(endDate);
  }
}
