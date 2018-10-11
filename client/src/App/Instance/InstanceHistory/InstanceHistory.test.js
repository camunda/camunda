import React from 'react';
import {shallow} from 'enzyme';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import * as api from 'modules/api/events/events';

import InstanceHistory from './InstanceHistory';
import InstanceLog from './InstanceLog';
import InstanceEvents from './InstanceEvents';
import * as Styled from './styled';
import {getEventLabel} from './service';
import {formatDate} from 'modules/utils/date';

const fooActivityEvents = [
  {
    eventType: 'fooCreated',
    eventSourceType: 'JOB',
    activityInstanceId: 'foo',
    dateTime: '2018-10-11T06:00:00.000+0000',
    metadata: {
      a: 'b'
    }
  },
  {
    eventSourceType: 'JOB',
    eventType: 'fooActiviated',
    activityInstanceId: 'foo',
    dateTime: '2018-10-11T06:15:00.000+0000'
  }
];

const barActivityEvents = [
  {
    eventType: 'barCreated',
    eventSourceType: 'INCIDENT',
    activityInstanceId: 'bar',
    dateTime: '2018-10-11T06:00:00.000+0000'
  },
  {
    eventSourceType: 'JOB',
    eventType: 'BAR_ACTIVATED',
    activityInstanceId: 'bar',
    dateTime: '2018-10-11T06:15:00.000+0000'
  }
];

const instanceEvents = [
  {
    eventSourceType: 'WORKFLOW_INSTANCE',
    eventType: 'baz',
    dateTime: '2018-10-11T05:00:00.000+0000',
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

const mockActivitiesDetails = {
  foo: {id: 'foo', name: 'foo name', activityId: 'fooAID'},
  bar: {id: 'bar', name: 'bar name', activityId: 'barAID'}
};

api.fetchEvents = mockResolvedAsyncFn(mockEvents);

describe('InstanceHistory', () => {
  it('should have the right initial state', () => {
    // given
    const instance = new InstanceHistory();

    // then
    expect(instance.state).toEqual({
      events: [],
      groupedEvents: [],
      selectedEventRow: {
        key: null,
        payload: null
      }
    });
  });

  it('should render a panel with InstanceLog section and Copyright', async () => {
    // given
    const mockProps = {
      instance: {id: 'someInstanceId'},
      selectedActivityInstanceId: 'foo',
      onActivityInstanceSelected: jest.fn()
    };

    const node = shallow(<InstanceHistory {...mockProps} />);

    await flushPromises();
    node.update();
    node.setProps({activitiesDetails: mockActivitiesDetails});
    node.update();

    // then
    // Pane
    expect(node.find(SplitPane.Pane)).toHaveLength(1);
    // Pane Header
    const PaneHeaderNode = node.find(SplitPane.Pane.Header);
    expect(PaneHeaderNode).toHaveLength(1);
    expect(PaneHeaderNode.children().text()).toContain('Instance History');
    // Pane Body
    const PaneBodyNode = node.find(Styled.PaneBody);
    expect(PaneBodyNode).toHaveLength(1);

    // Instance Log
    const InstanceLogNode = PaneBodyNode.find(InstanceLog);
    expect(InstanceLogNode).toHaveLength(1);
    expect(InstanceLogNode.prop('instance')).toEqual(mockProps.instance);
    expect(InstanceLogNode.prop('activitiesDetails')).toEqual(
      mockActivitiesDetails
    );
    expect(InstanceLogNode.prop('selectedActivityInstanceId')).toEqual(
      mockProps.selectedActivityInstanceId
    );
    expect(InstanceLogNode.prop('onActivityInstanceSelected')).toEqual(
      mockProps.onActivityInstanceSelected
    );

    // Instance Events
    const InstanceEventsNode = PaneBodyNode.find(InstanceEvents);
    expect(InstanceEventsNode).toHaveLength(1);
    const expectedEvents = fooActivityEvents.map(event => ({
      ...event,
      label: getEventLabel(event),
      metadata: {
        ...event.metadata,
        timestamp: formatDate(event.dateTime)
      }
    }));
    expect(InstanceEventsNode.prop('groupedEvents')).toEqual(expectedEvents);

    // Pane Footer
    const PaneFooterNode = node.find(Styled.PaneFooter);
    expect(PaneFooterNode).toHaveLength(1);

    // Copyright
    const CopyrightNode = PaneFooterNode.find(Copyright);
    expect(CopyrightNode).toHaveLength(1);

    // snapshot
    expect(node).toMatchSnapshot();
  });

  describe('handleEventRowChange', () => {
    it('should set the payload and key of the selected event row', () => {
      // given
      const mockProps = {
        instance: {}
      };
      const node = shallow(<InstanceHistory {...mockProps} />);
      const expectedPayload = {a: 'b'};
      const selectedEventRow = {
        key: 'foo',
        payload: JSON.stringify(expectedPayload)
      };

      // when
      node.instance().handleEventRowChange(selectedEventRow);
      node.update();

      // then
      expect(node.state('selectedEventRow')).toEqual({
        key: 'foo',
        payload: expectedPayload
      });
    });

    it('should set the payload to null if the selected row payload is not parsable', () => {
      // given
      const mockProps = {
        instance: {}
      };
      const node = shallow(<InstanceHistory {...mockProps} />);
      const selectedEventRow = {
        key: 'foo',
        payload: '{some unparsable jstring'
      };

      // when
      node.instance().handleEventRowChange(selectedEventRow);
      node.update();

      // then
      expect(node.state('selectedEventRow')).toEqual({
        key: 'foo',
        payload: null
      });
    });

    it("should reset the selected event row if it's selected again", () => {
      // given
      const mockProps = {
        instance: {}
      };
      const node = shallow(<InstanceHistory {...mockProps} />);
      const expectedPayload = {a: 'b'};
      const selectedEventRow = {
        key: 'foo',
        payload: JSON.stringify(expectedPayload)
      };
      node.setState({selectedEventRow: {key: 'foo', payload: expectedPayload}});
      node.update();

      // when
      node.instance().handleEventRowChange(selectedEventRow);
      node.update();

      // then
      expect(node.state('selectedEventRow')).toEqual({
        key: null,
        payload: null
      });
    });
  });
});
