import React from 'react';
import {shallow} from 'enzyme';

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

const fooGroupedEvents = {
  id: 'foo',
  name: 'foo name',
  events: fooActivityEvents
};
const barGroupedEvents = {
  id: 'foo',
  name: 'bar name',
  events: barActivityEvents
};

const mockGroupedEvents = [
  {...fooGroupedEvents},
  {...barGroupedEvents},
  ...instanceEvents
];

describe('InstanceEvents', () => {
  it('should render empty events container by default', () => {
    // given
    const node = shallow(<InstanceEvents />);

    // then
    const EventsContainerNode = node.find(Styled.EventsContainer);
    expect(EventsContainerNode).toHaveLength(1);
    expect(EventsContainerNode.children().length).toBe(0);
  });

  it('should render grouped events', async () => {
    // given
    const node = shallow(<InstanceEvents groupedEvents={mockGroupedEvents} />);

    // then
    // Foldable for each event and each group
    const FoldableNodes = node.find(Foldable);
    expect(FoldableNodes).toHaveLength(8);

    // Foo Foldable
    const FooFoldableNode = FoldableNodes.at(0);
    // Foo Summary
    const SummaryNode = FooFoldableNode.find(Foldable.Summary).at(0);
    expect(SummaryNode.prop('isBold')).toBe(true);
    expect(SummaryNode.contains(fooGroupedEvents.name));

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
