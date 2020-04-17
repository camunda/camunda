/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Form, DateRangeInput} from 'components';
import {convertFilterToState, convertStateToFilter, isValid} from '../../date';

export default class DateInput extends React.Component {
  static defaultFilter = {
    valid: false,
    type: '',
    unit: '',
    customNum: '2',
    startDate: null,
    endDate: null,
  };

  componentDidMount() {
    this.props.setValid(false);
  }

  render() {
    return (
      <Form>
        <DateRangeInput
          {...this.props.filter}
          disabled={this.props.disabled}
          onChange={(change) => {
            const newFilter = {...this.props.filter, ...change};
            this.props.changeFilter(newFilter);
            this.props.setValid(isValid(newFilter));
          }}
        />
      </Form>
    );
  }

  static parseFilter = ({data}) => convertFilterToState(data.data);

  static addFilter = (addFilter, variable, filter, filterForUndefined) => {
    addFilter({
      type: 'variable',
      data: {
        name: variable.name,
        type: variable.type,
        filterForUndefined,
        data: convertStateToFilter(filter),
      },
    });
  };
}
