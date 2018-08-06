import React from 'react';
import {shallow} from 'enzyme';

import * as api from 'modules/api/events/events';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import InstanceEvents from './InstanceEvents';
import * as Styled from './styled';
import Foldable from './Foldable';

const fooActivityEvents = [
  {
    eventType: 'fooCreated',
    activityInstanceId: 'foo',
    metadata: {
      a: 'b'
    }
  },
  {
    eventType: 'fooActiviated',
    activityInstanceId: 'foo'
  }
];

const barActivityEvents = [
  {
    eventType: 'barCreated',
    activityInstanceId: 'bar'
  },
  {
    eventType: 'barActivated',
    activityInstanceId: 'bar'
  }
];

const instanceEvents = [
  {
    eventType: 'baz',
    metadata: {
      c: {
        d: 'e',
        e: null
      },
      f: null
    }
  }
];

const mockEvents = [
  ...fooActivityEvents,
  ...barActivityEvents,
  ...instanceEvents
];

api.fetchEvents = mockResolvedAsyncFn(mockEvents);

const mockInstance = {id: 'foo'};

const fooActivityDetails = {name: 'foo name'};
const barActivityDetails = {name: 'bar name'};
const mockActivitiesDetails = {
  foo: fooActivityDetails,
  bar: barActivityDetails
};

describe('InstanceEvents', () => {
  beforeEach(() => {
    api.fetchEvents.mockClear();
  });

  it('should render empty events container by default', () => {
    // given
    const node = shallow(<InstanceEvents instance={mockInstance} />);

    // then
    expect(node.state()).toEqual({
      events: null,
      groupedEvents: null
    });
    const EventsContainerNode = node.find(Styled.EventsContainer);
    expect(EventsContainerNode).toHaveLength(1);
    expect(EventsContainerNode.children().length).toBe(0);
  });

  it("should not render grouped events if events or activities didn't change", async () => {
    // given
    const node = shallow(<InstanceEvents instance={mockInstance} />);
    await flushPromises();
    node.update();

    // then
    expect(node.state()).toEqual({
      events: mockEvents,
      groupedEvents: null
    });
    const EventsContainerNode = node.find(Styled.EventsContainer);
    expect(EventsContainerNode).toHaveLength(1);
    expect(EventsContainerNode.children().length).toBe(0);
  });

  it('should render grouped events if events or activities changed', async () => {
    // given
    const expectedGroupedEvents = [
      {
        events: fooActivityEvents,
        name: fooActivityDetails.name
      },
      {
        events: barActivityEvents,
        name: barActivityDetails.name
      },
      ...instanceEvents
    ];
    const node = shallow(<InstanceEvents instance={mockInstance} />);
    await flushPromises();
    node.setProps({activitiesDetails: mockActivitiesDetails});
    node.update();

    // then
    expect(node.state().events).toEqual(mockEvents);
    expect(node.state('groupedEvents')).toEqual(expectedGroupedEvents);
    // Foldable for each event and each group
    const FoldableNodes = node.find(Foldable);
    expect(FoldableNodes).toHaveLength(8);

    // Foo Foldable
    const FooFoldableNode = FoldableNodes.at(0);
    // Foo Summary
    const SummaryNode = FooFoldableNode.find(Foldable.Summary).at(0);
    expect(SummaryNode.prop('bold')).toBe(true);
    expect(SummaryNode.contains(fooActivityDetails.name));

    // Foo Details
    const FoldableDetailsNode = FooFoldableNode.find(Foldable.Details).at(0);
    expect(FoldableDetailsNode).toHaveLength(1);

    // Foo Events Foldables
    expect(FoldableDetailsNode.find(Foldable)).toHaveLength(
      fooActivityEvents.length
    );

    // Instance Events Foldables
    const InstanceFoldableNode = FoldableNodes.at(6);
    // Instance Event Summary
    const InstanceEventSummaryNode = InstanceFoldableNode.find(
      Foldable.Summary
    ).at(0);
    expect(InstanceEventSummaryNode.prop('bold')).not.toBe(true);
    expect(InstanceEventSummaryNode.contains(instanceEvents[0].eventType));

    // Instance Events Foldables
    expect(InstanceFoldableNode.find(Foldable)).toHaveLength(2);

    expect(node).toMatchSnapshot();
  });
});
