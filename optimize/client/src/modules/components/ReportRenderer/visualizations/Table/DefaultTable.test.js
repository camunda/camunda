/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import DefaultTable from './DefaultTable';

jest.mock('./processRawData', () => jest.fn().mockReturnValue({}));

jest.mock('./processDefaultData', () =>
  jest.fn().mockReturnValue({head: ['col1', 'col2', {id: 'col3'}]})
);

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([]),
  };
});

beforeEach(() => {
  jest.clearAllMocks();
});

const testDefinition = {key: 'definitionKey', versions: ['ver1'], tenantIds: ['id1']};
const report = {
  data: {
    definitions: [testDefinition],
    groupBy: {
      value: {},
      type: '',
    },
    view: {properties: ['duration']},
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: [],
        columnOrder: ['col1', 'col2', 'col3'],
      },
    },
    visualization: 'table',
  },
  result: {
    instanceCount: 5,
    data: [],
  },
};

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  report,
};

it('should update configuration when arranging columns', () => {
  const spy = jest.fn();
  const node = shallow(<DefaultTable {...props} updateReport={spy} />);

  runAllEffects();

  node.find('ColumnRearrangement').prop('onChange')(0, 2);

  expect(spy).toHaveBeenCalledWith({
    configuration: {tableColumns: {columnOrder: {$set: ['col2', 'col3', 'col1']}}},
  });
});
