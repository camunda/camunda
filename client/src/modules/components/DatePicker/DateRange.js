/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Calendar} from 'react-date-range';

import {adjustRange} from './service';
import moment from 'moment';

import './DateRange.scss';

const theme = {
  DayInRange: {
    color: 'black',
    background: '#eeeeee'
  },
  DayActive: {
    background: '#871020'
  },
  DaySelected: {
    background: '#871020'
  },
  DayPassive: {
    cursor: 'default'
  }
};

export default class DateRange extends React.Component {
  constructor(props) {
    super(props);

    this.state = adjustRange({
      startLink: this.props.startDate.clone(),
      endLink: this.props.endDate.clone()
    });
  }

  render() {
    const range = {
      startDate: this.props.startDate,
      endDate: this.props.endDate
    };

    return (
      <div>
        <Calendar
          format="this.props.format"
          link={this.state.startLink}
          linkCB={this.changeStartMonth}
          range={range}
          theme={theme}
          minDate={this.props.minDate}
          maxDate={this.props.maxDate}
          firstDayOfWeek={1}
          onChange={this.props.onDateChange}
          classNames={this.getClassesForCalendar(true)}
        />

        <Calendar
          format="this.props.format"
          link={this.state.endLink}
          linkCB={this.changeEndMonth}
          theme={theme}
          range={range}
          minDate={this.props.minDate}
          maxDate={this.props.maxDate}
          firstDayOfWeek={1}
          onChange={this.props.onDateChange}
          classNames={this.getClassesForCalendar(false)}
        />
      </div>
    );
  }

  getClassesForCalendar(first) {
    if (first && this.state.innerArrowsDisabled) {
      return {
        nextButton: 'DateRange__calendarButton--hidden'
      };
    }

    if (!first && this.state.innerArrowsDisabled) {
      return {
        prevButton: 'DateRange__calendarButton--hidden'
      };
    }
  }

  static getDerivedStateFromProps({startDate, endDate}) {
    return {
      startLink: startDate.isValid() ? startDate.clone() : moment(),
      endLink: endDate.isValid() ? endDate.clone() : moment()
    };
  }

  changeStartMonth = direction => this.changeMonth('startLink', direction);

  changeEndMonth = direction => this.changeMonth('endLink', direction);

  changeMonth = (name, direction) => {
    const newLink = this.state[name].clone().add(direction, 'months');

    this.setState(
      adjustRange({
        ...this.state,
        [name]: newLink
      })
    );
  };
}
