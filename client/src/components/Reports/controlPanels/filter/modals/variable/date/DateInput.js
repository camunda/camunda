/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Form, DateRangeInput} from 'components';
import {convertFilterToState, convertStateToFilter, DateFilterPreview, isValid} from '../../date';

import './DateInput.scss';

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
    this.props.setValid(isValid(this.props.filter));
  }

  render() {
    const {filter, variable, changeFilter, setValid} = this.props;

    return (
      <Form className="DateInput">
        <DateRangeInput
          {...filter}
          onChange={(change) => {
            const newFilter = {...filter, ...change};
            changeFilter(newFilter);
            setValid(isValid(newFilter));
          }}
        />
        <Form.Group className="previewContainer">
          {isValid(filter) && (
            <DateFilterPreview
              filterType="variable"
              variableName={variable.name}
              filter={convertStateToFilter(filter)}
            />
          )}
        </Form.Group>
      </Form>
    );
  }

  static parseFilter = ({data}) => convertFilterToState(data.data);

  static addFilter = (addFilter, variable, filter) => {
    addFilter({
      type: 'variable',
      data: {
        name: variable.name,
        type: variable.type,
        data: convertStateToFilter(filter),
      },
    });
  };
}
