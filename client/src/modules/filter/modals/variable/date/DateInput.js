/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Form, DateRangeInput} from 'components';
import {convertFilterToState, convertStateToFilter, DateFilterPreview, isValid} from '../../date';
import UndefinedOptions from './UndefinedOptions';

import './DateInput.scss';

export default class DateInput extends React.Component {
  static defaultFilter = {
    valid: false,
    type: '',
    unit: '',
    customNum: '2',
    startDate: null,
    endDate: null,
    includeUndefined: false,
    excludeUndefined: false,
  };

  componentDidMount() {
    this.props.setValid?.(isValid(this.props.filter));
  }

  changeFilter = (newFilter) => {
    this.props.changeFilter(newFilter);
    this.props.setValid?.(isValid(newFilter));
  };

  render() {
    const {filter, variable} = this.props;
    const {includeUndefined, excludeUndefined} = filter;

    return (
      <Form className="DateInput">
        <DateRangeInput
          {...filter}
          onChange={(change) => this.changeFilter({...filter, ...change, excludeUndefined: false})}
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
        <UndefinedOptions
          includeUndefined={includeUndefined}
          excludeUndefined={excludeUndefined}
          changeIncludeUndefined={(includeUndefined) =>
            this.changeFilter({...filter, includeUndefined, excludeUndefined: false})
          }
          changeExcludeUndefined={(excludeUndefined) =>
            this.changeFilter({...DateInput.defaultFilter, excludeUndefined})
          }
        />
      </Form>
    );
  }

  static parseFilter = ({data}) => convertFilterToState(data.data);

  static addFilter = (addFilter, type, variable, filter, applyTo) => {
    const filterData = convertStateToFilter(filter);

    addFilter({
      type,
      data: {
        name: variable.name,
        type: variable.type,
        data: {...filterData, type: filterData.type || 'fixed'},
      },
      appliedTo: [applyTo?.identifier],
    });
  };

  static isValid = isValid;
}
