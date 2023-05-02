/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component} from 'react';
import {parseISO, isAfter, isBefore} from 'date-fns';

import {format} from 'dates';

import DateFields, {DateFieldName} from './DateFields';
import {isDateValid, DATE_FORMAT} from './service';

interface DatePickerProps {
  initialDates: {startDate: Date | null; endDate: Date | null};
  type: string;
  forceOpen?: boolean;
  onDateChange: (options: {endDate: Date | null; startDate: Date | null; valid?: boolean}) => void;
}

type DatePickerState = Record<DateFieldName, string>;

export default class DatePicker extends Component<DatePickerProps, DatePickerState> {
  constructor(props: DatePickerProps) {
    super(props);

    const {startDate, endDate} = props.initialDates || {};
    this.state = {
      startDate: startDate ? format(+startDate, DATE_FORMAT) : '',
      endDate: endDate ? format(+endDate, DATE_FORMAT) : '',
    };
  }

  setDates = (dates: DatePickerState) => this.setState({...dates});

  onDateChange = (name: DateFieldName, date: string) => {
    const {type} = this.props;
    const dateObj = parseISO(date);

    this.setState(
      {
        [name]: date,
      } as Record<DateFieldName, string>,
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
          ((name === 'startDate' && endDate && isAfter(dateObj, endDate)) ||
            (name === 'endDate' && startDate && isBefore(dateObj, startDate)))
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

function isValid(type: string, startDate: string, endDate: string) {
  if (type === 'between') {
    return isDateValid(startDate) && isDateValid(endDate);
  } else if (type === 'after') {
    return isDateValid(startDate);
  } else if (type === 'before') {
    return isDateValid(endDate);
  }
}
