/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Deleter, Dropdown} from 'components';

import {deleteEvents, loadIngestedEvents} from './service';
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
  deleteEvents: jest.fn(),
}));

jest.mock(
  'debouncePromise',
  () =>
    () =>
    (fn, delay, ...args) =>
      fn(...args)
);
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

  node.find('SearchInput').simulate('change', {target: {value: 'invoice'}});
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

it('should be possible to delete multiple events from the table', () => {
  const node = shallow(<IngestedEvents {...props} />);

  runAllEffects();
  node
    .find('Table')
    .prop('body')[0][0]
    .props.onSelect({target: {checked: true}});
  node
    .find('Table')
    .prop('body')[1][0]
    .props.onSelect({target: {checked: true}});

  node.find(Dropdown.Option).simulate('click');

  expect(node.find(Deleter).prop('entity')).toBe(true);
  node.find(Deleter).prop('deleteEntity')();

  expect(deleteEvents).toHaveBeenCalledWith([
    '8edc4160-74e5-4ffc-af59-2d281cf5aca347',
    '7edc4160-74e5-4ffc-af59-2d281cf5aca346',
  ]);
});

it('should select all events in view', () => {
  const node = shallow(<IngestedEvents {...props} />);

  runAllEffects();

  node
    .find('Table')
    .prop('head')[0]
    .label.props.onSelect({target: {checked: true}});

  expect(node.find('.selectionActions').prop('label')).toBe('2 Selected');
});
