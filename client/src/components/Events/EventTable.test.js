/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Table, Switch} from 'components';

import {loadEvents} from './service';
import EventTableWithErrorHandling from './EventTable';

const EventTable = EventTableWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadEvents: jest.fn().mockReturnValue([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      eventLabel: 'Order Processed',
      count: 10,
    },
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderAccepted',
      eventLabel: 'Order Accepted',
      count: null,
    },
  ]),
  isNonTimerEvent: jest.fn().mockReturnValue(false),
}));

jest.mock(
  'debouncePromise',
  () =>
    () =>
    (fn, delay, ...args) =>
      fn(...args)
);

const props = {
  selection: {id: 'a', $instanceOf: (type) => type === 'bpmn:Event'},
  mappings: {
    a: {
      start: null,
      end: {
        group: 'eventGroup',
        source: 'order-service',
        eventName: 'OrderProcessed',
        eventLabel: 'Order Processed',
      },
    },
  },
  onMappingChange: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  xml: 'some xml',
  eventSources: [{type: 'external', configuration: {includeAllGroups: true}}],
  onSelectEvent: jest.fn(),
};

const defaultSorting = {by: 'group', order: 'asc'};

it('should match snapshot', async () => {
  const node = shallow(<EventTable {...props} />);

  await flushPromises();

  expect(node).toMatchSnapshot();
});

it('should load events', () => {
  loadEvents.mockClear();
  shallow(<EventTable {...props} />);

  expect(loadEvents).toHaveBeenCalled();
});

it('should disable table if no node is selected', async () => {
  const node = shallow(<EventTable {...props} selection={null} />);

  await flushPromises();

  expect(node.find(Table).prop('body')[0].props.className).toContain('disabled');
});

it('should allow searching for events', async () => {
  const node = shallow(<EventTable {...props} />);

  node.setState({showSuggested: false});

  loadEvents.mockClear();

  const toolbar = shallow(node.find(Table).prop('toolbar'));
  toolbar.find('TableToolbarSearch').prop('onChange')({
    target: {value: 'some String'},
  });

  await flushPromises();

  expect(loadEvents).toHaveBeenCalledWith(
    {eventSources: props.eventSources},
    'some String',
    defaultSorting
  );
});

it('should call callback when changing mapping', async () => {
  const spy = jest.fn();
  const node = shallow(<EventTable {...props} onMappingChange={spy} />);

  await flushPromises();

  node
    .find(Table)
    .prop('body')[0]
    .content[0].props.onSelect({target: {checked: false}});

  await flushPromises();

  expect(spy).toHaveBeenCalledWith(
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      eventLabel: 'Order Processed',
    },
    false
  );
});

it('should pass payload to backend when loading events for suggestions', () => {
  loadEvents.mockClear();
  shallow(<EventTable {...props} />);

  expect(loadEvents).toHaveBeenCalledWith(
    {
      targetFlowNodeId: 'a',
      xml: 'some xml',
      mappings: props.mappings,
      eventSources: props.eventSources,
    },
    '',
    defaultSorting
  );
});

it('should load updated suggestions when the selection changes', () => {
  const node = shallow(<EventTable {...props} />);

  loadEvents.mockClear();

  node.setProps({selection: {id: 'b', $instanceOf: (type) => type === 'bpmn:Event'}});

  expect(loadEvents).toHaveBeenCalled();
});

it('should not reload events if suggestions are not activated', () => {
  const node = shallow(<EventTable {...props} />);

  const toolbar = shallow(node.find(Table).prop('toolbar'));
  toolbar.find(Switch).prop('onChange')({
    target: {checked: false},
  });

  loadEvents.mockClear();

  node.setProps({selection: {id: 'b', $instanceOf: (type) => type === 'bpmn:Event'}});

  expect(loadEvents).not.toHaveBeenCalled();
});

it('should mark suggested events', async () => {
  loadEvents.mockReturnValueOnce([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      count: 10,
      suggested: true,
    },
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderAccepted',
      count: 10,
      suggested: false,
    },
  ]);

  const node = shallow(<EventTable {...props} />);

  await flushPromises();

  const events = node.find(Table).prop('body');
  expect(events[0].props.className).toContain('suggested');
  expect(events[1].props.className).not.toContain('suggested');
});

it('should hide events Suggestion if there are any camunda event sources', () => {
  const node = shallow(
    <EventTable
      {...props}
      eventSources={[...props.eventSources, {type: 'camunda', configuration: {}}]}
    />
  );

  expect(node.find('.header Switch')).not.toExist();
});

it('should hide events Suggestion if an external group were added', () => {
  const node = shallow(
    <EventTable
      {...props}
      eventSources={[
        ...props.eventSources,
        {type: 'external', configuration: {group: 'test', includeAllGroups: false}},
      ]}
    />
  );

  expect(node.find('.header Switch')).not.toExist();
});

it('should not show events from hidden sources in the table', async () => {
  loadEvents.mockReturnValueOnce([
    {
      group: 'eventGroup',
      source: 'order-service',
      eventName: 'OrderProcessed',
      count: 10,
    },
    {
      group: 'bookrequest',
      source: 'camunda',
      eventName: 'startEvent',
      count: 10,
    },
  ]);

  const node = shallow(
    <EventTable
      {...props}
      eventSources={[
        {type: 'external', hidden: true},
        {type: 'camunda', configuration: {processDefinitionKey: 'bookrequest'}},
      ]}
    />
  );

  await flushPromises();

  const events = node.find(Table).prop('body');
  expect(events).toHaveLength(1);
  expect(events[0].content).toContain('bookrequest');
});

it('should invoke onSelectEvent when clicking on an element', async () => {
  const node = shallow(<EventTable {...props} />);

  await flushPromises();

  const events = node.find(Table).prop('body');
  events[0].props.onClick({target: {getAttribute: () => null, closest: () => null}});

  expect(props.onSelectEvent).toHaveBeenCalledWith({
    count: 10,
    eventName: 'OrderProcessed',
    group: 'eventGroup',
    source: 'order-service',
    eventLabel: 'Order Processed',
  });
});

it('should reset the selected event when clicking on the checkbox', async () => {
  const node = shallow(<EventTable {...props} />);

  await flushPromises();

  const events = node.find(Table).prop('body');
  events[0].props.onClick({target: {getAttribute: () => 'checkbox'}});

  expect(props.onSelectEvent).toHaveBeenCalledWith(null);
});

it('Should collapse the table on collapse button click', () => {
  const node = shallow(<EventTable {...props} />);

  const toolbar = shallow(node.find(Table).prop('toolbar'));
  toolbar.find('.collapseButton').simulate('click');

  expect(node.find(Table).hasClass('collapsed')).toBe(true);
});

it('Should invoke loadEvents when updating the column sorting', () => {
  loadEvents.mockClear();
  const node = shallow(<EventTable {...props} />);

  node.find('Table').prop('updateSorting')('eventName', 'desc');

  expect(loadEvents).toHaveBeenCalledWith(
    {
      targetFlowNodeId: 'a',
      xml: 'some xml',
      mappings: props.mappings,
      eventSources: props.eventSources,
    },
    '',
    {by: 'eventName', order: 'desc'}
  );
});
