import React from 'react';
import {shallow} from 'enzyme';

import * as api from 'modules/api/events/events';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import InstanceEvents from './InstanceEvents';
import * as Styled from './styled';
import ExpansionPanel from './ExpansionPanel';

const fooActivityEvents = [
  {
    eventType: 'fooCreated',
    activityInstanceId: 'foo'
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
    eventType: 'baz'
  }
];

const mockEvents = [
  ...fooActivityEvents,
  ...barActivityEvents,
  ...instanceEvents
];

api.fetchEvents = mockResolvedAsyncFn(mockEvents);

const mockInstance = {id: 'foo'};

const fooActivityDetails = {id: 'foo', name: 'foo name'};
const barActivityDetails = {id: 'bar', name: 'bar name'};
const mockActivitiesDetails = [fooActivityDetails, barActivityDetails];

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
    // ExpansionPanel for each event and each group
    const ExpansionPanelNodes = node.find(ExpansionPanel);
    expect(ExpansionPanelNodes).toHaveLength(mockEvents.length + 2);

    // Foo ExpansionPanel
    const FooExpansionPanelNode = ExpansionPanelNodes.at(0);
    // Foo Summary
    const SummaryNode = FooExpansionPanelNode.find(ExpansionPanel.Summary).at(
      0
    );
    expect(SummaryNode.prop('bold')).toBe(true);
    expect(SummaryNode.contains(fooActivityDetails.name));

    // Foo Details
    const ExpansionPanelDetailsNode = FooExpansionPanelNode.find(
      ExpansionPanel.Details
    ).at(0);
    expect(ExpansionPanelDetailsNode).toHaveLength(1);

    // Foo Events ExpansionPanels
    expect(ExpansionPanelDetailsNode.find(ExpansionPanel)).toHaveLength(
      fooActivityEvents.length
    );

    // Instance Events ExpnsionPanels
    const InstanceExpansionPanelNode = ExpansionPanelNodes.at(1);
    // Instance Event Summary
    const InstanceEventSummaryNode = InstanceExpansionPanelNode.find(
      ExpansionPanel.Summary
    ).at(0);
    expect(InstanceEventSummaryNode.prop('bold')).not.toBe(true);
    expect(InstanceEventSummaryNode.contains(instanceEvents[0].eventType));

    expect(node).toMatchSnapshot();
  });
});
