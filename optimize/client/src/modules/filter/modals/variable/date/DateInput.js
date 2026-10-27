/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import {Form, Stack} from '@carbon/react';

import {DateRangeInput} from 'components';

import {convertFilterToState, convertStateToFilter, DateFilterPreview, isValid} from '../../date';

import UndefinedOptions from './UndefinedOptions';

import './DateInput.scss';

export default class DateInput extends Component {
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

  changeFilter = (newFilter) => {
    this.props.changeFilter(newFilter);
  };

  render() {
    const {filter, variable} = this.props;
    const {includeUndefined, excludeUndefined} = filter;

    return (
      <Form className="DateInput">
        <Stack gap={6}>
          <DateRangeInput
            {...filter}
            onChange={(change) =>
              this.changeFilter({...filter, ...change, excludeUndefined: false})
            }
          />
          {isValid(filter) && (
            <DateFilterPreview
              filterType="variable"
              variableName={variable.name}
              filter={convertStateToFilter(filter)}
            />
          )}
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
        </Stack>
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
