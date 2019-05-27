/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';
import {reportConfig} from 'services';

import ReportDropdown from './ReportDropdown';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      ...rest.reportConfig,
      process: {
        getLabelFor: jest.fn().mockReturnValue('foo'),
        options: {
          view: [{data: 'foo', label: 'viewfoo'}],
          groupBy: [
            {data: 'foo', label: 'groupbyfoo'},
            {options: 'inputVariable', label: 'Input Variable'}
          ],
          visualization: [{data: 'foo', label: 'visualizationfoo'}]
        },
        isAllowed: jest.fn().mockReturnValue(true)
      }
    }
  };
});

const config = {
  type: 'process',
  field: 'view',
  value: 'foo',
  variables: {inputVariable: []},
  disabled: false,
  onChange: jest.fn()
};

it('should disable options which would create a wrong combination', () => {
  reportConfig.process.isAllowed.mockReturnValue(false);

  const node = shallow(<ReportDropdown {...config} />);

  expect(node.find(Dropdown.Option)).toBeDisabled();
});

it('should disable the variable groupby submenu if there are no variables', () => {
  const node = shallow(<ReportDropdown {...config} field="groupBy" />);

  expect(node.find(Dropdown.Submenu)).toBeDisabled();
});
