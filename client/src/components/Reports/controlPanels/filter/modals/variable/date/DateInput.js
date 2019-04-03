/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {DatePicker} from 'components';

export default class DateInput extends React.Component {
  static defaultFilter = {type: 'fixed', startDate: moment(), endDate: moment()};

  componentDidMount() {
    this.props.setValid(true);
  }

  render() {
    return (
      <DatePicker
        onDateChange={this.onDateChange}
        initialDates={{
          startDate: this.props.filter.startDate,
          endDate: this.props.filter.endDate
        }}
      />
    );
  }

  onDateChange = ({startDate, endDate, valid}) => {
    this.props.changeFilter({startDate, endDate});
    this.props.setValid(valid);
  };

  static parseFilter = ({
    data: {
      data: {start, end}
    }
  }) => {
    return {
      startDate: moment(start),
      endDate: moment(end)
    };
  };

  static addFilter = (addFilter, variable, filter) => {
    addFilter({
      type: 'variable',
      data: {
        name: variable.name,
        type: variable.type,
        data: {
          start: filter.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
          end: filter.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
        }
      }
    });
  };
}
