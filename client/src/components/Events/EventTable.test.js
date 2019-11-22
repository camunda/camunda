/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Table} from 'components';

import {loadEvents} from './service';
import EventTableWithErrorHandling from './EventTable';

const EventTable = EventTableWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadEvents: jest.fn().mockReturnValue([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      count: 10
    },
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderAccepted',
      count: 10
    }
  ])
}));

jest.mock('debounce', () => fn => fn);

const props = {
  selection: {id: 'a', $instanceOf: type => type === 'bpmn:Event'},
  mappings: {
    a: {
      start: null,
      end: {
        group: 'eventGroup',
        source: 'order-service',
        eventName: 'OrderProcessed'
      }
    }
  },
  onChange: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should match snapshot', () => {
  const node = shallow(<EventTable {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load events', () => {
  loadEvents.mockClear();
  shallow(<EventTable {...props} />);

  expect(loadEvents).toHaveBeenCalled();
});

it('should disable table if no node is selected', () => {
  const node = shallow(<EventTable {...props} selection={null} />);

  expect(node.find(Table).prop('body')[0].props.className).toBe('disabled');
});

it('should allow searching for events', () => {
  const node = shallow(<EventTable {...props} />);
  loadEvents.mockClear();

  node.find('.searchInput').prop('onChange')({target: {value: 'some String'}});

  expect(loadEvents).toHaveBeenCalledWith('some String');
});

it('should call callback when changing mapping', () => {
  const spy = jest.fn();
  const node = shallow(<EventTable {...props} onChange={spy} />);

  node
    .find(Table)
    .prop('body')[0]
    .content[0].props.onChange({target: {checked: false}});

  expect(spy).toHaveBeenCalledWith(
    {group: 'eventGroup', source: 'order-service', eventName: 'OrderProcessed'},
    false
  );
});
