/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {DatePicker} from 'components';

export default class DateInput extends React.Component {
  static defaultFilter = {type: 'fixed', startDate: null, endDate: null};

  componentDidMount() {
    this.props.setValid(false);
  }

  render() {
    return (
      <DatePicker
        onDateChange={this.onDateChange}
        initialDates={{
          startDate: this.props.filter.startDate,
          endDate: this.props.filter.endDate
        }}
        disabled={this.props.disabled}
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

  static addFilter = (addFilter, variable, filter, filterForUndefined) => {
    addFilter({
      type: 'variable',
      data: {
        name: variable.name,
        type: variable.type,
        filterForUndefined,
        data: {
          start: filter.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
          end: filter.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')
        }
      }
    });
  };
}
