import React from 'react';
import {shallow} from 'enzyme';

import {ACTIVITY_STATE, EVENT_TYPE, EVENT_SOURCE_TYPE} from 'modules/constants';

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
    eventType: EVENT_TYPE.CREATED,
    eventSourceType: EVENT_SOURCE_TYPE.INCIDENT,
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
  events: barActivityEvents,
  state: ACTIVITY_STATE.INCIDENT
};

const groupedEvents = [
  {...fooGroupedEvents},
  {...barGroupedEvents},
  ...instanceEvents
];

const mockProps = {
  groupedEvents,
  onEventRowChanged: jest.fn(),
  selectedEventRow: {
    key: null,
    payload: null
  }
};

describe('InstanceEvents', () => {
  beforeEach(() => {
    mockProps.onEventRowChanged.mockClear();
  });

  it('should render empty events container by default', () => {
    // given
    const node = shallow(
      <InstanceEvents
        groupedEvents={[]}
        onEventRowChanged={mockProps.onEventRowChanged}
      />
    );

    // then
    const EventsContainerNode = node.find(Styled.EventsContainer);
    expect(EventsContainerNode).toHaveLength(1);
    expect(EventsContainerNode.children().length).toBe(0);
  });

  it('should render grouped events', async () => {
    // given
    const node = shallow(<InstanceEvents {...mockProps} />);

    // then
    // Foldable for each event and each group
    const FoldableNodes = node.find(Foldable);
    expect(FoldableNodes).toHaveLength(8);

    // Foo Foldable
    const FooFoldableNode = FoldableNodes.findWhere(
      node => node.key() && node.key().includes('foo')
    );

    // Foo Summary
    const SummaryNode = FooFoldableNode.find(Foldable.Summary).at(0);
    expect(SummaryNode.contains(fooGroupedEvents.name)).toBe(true);

    // Foo Details
    const FoldableDetailsNode = FooFoldableNode.find(Foldable.Details).at(0);
    expect(FoldableDetailsNode).toHaveLength(1);

    // Foo Events Foldables
    expect(FoldableDetailsNode.find(Foldable)).toHaveLength(
      fooActivityEvents.length
    );

    // Bar Foldable
    const BarFoldableNode = FoldableNodes.findWhere(
      node => node.key() && node.key().includes('bar')
    );
    // Bar Summary
    const BarSummaryNode = BarFoldableNode.find(Foldable.Summary).at(0);
    expect(BarSummaryNode.contains(barGroupedEvents.name)).toBe(true);
    expect(BarSummaryNode.find(Styled.IncidentIcon)).toHaveLength(1);

    // Bar Details
    const BarFoldableDetailsNode = BarFoldableNode.find(Foldable.Details).at(0);
    expect(BarFoldableDetailsNode).toHaveLength(1);

    // Bar Events Foldables
    expect(BarFoldableDetailsNode.find(Foldable)).toHaveLength(
      barActivityEvents.length
    );
    const BarIncidentEventNode = BarFoldableDetailsNode.find(Foldable).at(0);
    const BarIncidentEventSummaryNode = BarIncidentEventNode.find(
      Foldable.Summary
    );
    expect(BarIncidentEventSummaryNode.prop('isOpenIncidentEvent')).toBe(true);
    expect(BarIncidentEventSummaryNode.find(Styled.IncidentIcon)).toHaveLength(
      1
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

  describe('selection', () => {
    it('should apply selection to the proper row', () => {
      // given
      const selectedEventRow = {key: 'fooCreated0', payload: {}};
      const node = shallow(
        <InstanceEvents {...mockProps} selectedEventRow={selectedEventRow} />
      );

      // then
      expect(
        node.find(`[data-test="${selectedEventRow.key}"]`).prop('isSelected')
      ).toBe(true);
    });
  });
});
