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

const mockActivitiesDetails = {
  foo: {name: 'foo name'},
  bar: {name: 'bar name'}
};

const fooGroupedEvents = {
  ...mockActivitiesDetails.foo,
  events: fooActivityEvents
};

const barGroupedEvents = {
  ...mockActivitiesDetails.bar,
  events: barActivityEvents
};

const expectedGroupedEvents = [
  {...fooGroupedEvents},
  {...barGroupedEvents},
  ...instanceEvents
];

api.fetchEvents = mockResolvedAsyncFn(mockEvents);

describe('InstanceHistory', () => {
  it('should have the right initial state', () => {
    // given
    const instance = new InstanceHistory();

    // then
    expect(instance.state).toEqual({
      events: null,
      groupedEvents: null
    });
  });

  it('should render a pane with InstanceLog section and Copyright', async () => {
    // given
    const mockProps = {
      instance: {id: 'someInstanceId'},
      selectedActivity: 'foo',
      onActivitySelected: jest.fn()
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
    expect(InstanceLogNode.prop('selectedActivity')).toEqual(
      mockProps.selectedActivity
    );
    expect(InstanceLogNode.prop('onActivitySelected')).toEqual(
      mockProps.onActivitySelected
    );

    // Instance Events
    const InstanceEventsNode = PaneBodyNode.find(InstanceEvents);
    expect(InstanceEventsNode).toHaveLength(1);
    expect(InstanceEventsNode.prop('groupedEvents')).toEqual(
      expectedGroupedEvents
    );

    // Pane Footer
    const PaneFooterNode = node.find(Styled.PaneFooter);
    expect(PaneFooterNode).toHaveLength(1);
    // Copyright
    const CopyrightNode = PaneFooterNode.find(Copyright);
    expect(CopyrightNode).toHaveLength(1);
    // snapshot
    expect(node).toMatchSnapshot();
  });
});
