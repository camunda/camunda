/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Input} from 'components';

import {loadIngestedEvents} from './service';
import {IngestedEvents} from './IngestedEvents';

jest.mock('./service', () => ({
  loadIngestedEvents: jest.fn().mockReturnValue({
    offset: 0,
    limit: 20,
    total: 2,
    results: [
      {
        id: '8edc4160-74e5-4ffc-af59-2d281cf5aca347',
        traceId: 'correlationValue_3',
        group: 'eventGroup',
        source: 'order-service',
        eventName: 'InvoiceProcessed',
        timestamp: 'Invoice Processed',
      },
      {
        id: '7edc4160-74e5-4ffc-af59-2d281cf5aca346',
        traceId: 'correlationValue_3',
        group: 'eventGroup',
        source: 'order-service',
        eventName: 'OrderProcessed',
        timestamp: 'Order Processed',
      },
    ],
  }),
}));

jest.mock('debounce', () => (fn) => fn);

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should match snapshot', () => {
  const node = shallow(<IngestedEvents {...props} />);

  runAllEffects();

  expect(node).toMatchSnapshot();
});

it('should load events initially', () => {
  loadIngestedEvents.mockClear();
  shallow(<IngestedEvents {...props} />);

  runAllEffects();
  expect(loadIngestedEvents).toHaveBeenCalled();
});

it('should add sorting and search params to the load event request', () => {
  loadIngestedEvents.mockClear();
  const node = shallow(<IngestedEvents {...props} />);

  node.find(Input).simulate('change', {target: {value: 'invoice'}});
  runAllEffects();
  node.find('Table').prop('updateSorting')('group', 'asc');
  node.find('Table').prop('fetchData')({pageSize: 50, pageIndex: 2});
  expect(loadIngestedEvents).toHaveBeenCalledWith({
    limit: 50,
    offset: 100,
    searchTerm: 'invoice',
    sortBy: 'group',
    sortOrder: 'asc',
  });
});
