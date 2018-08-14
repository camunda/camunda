import React from 'react';
import {shallow} from 'enzyme';

import SplitPane from 'modules/components/SplitPane';
import Copyright from 'modules/components/Copyright';

import InstanceHistory from './InstanceHistory';
import InstanceLog from './InstanceLog';
import InstanceEvents from './InstanceEvents';
import * as Styled from './styled';

describe('InstanceHistory', () => {
  it('should render a pane with InstanceLog section and Copyright', () => {
    // given
    const mockProps = {
      instance: {id: 'foo'},
      activitiesDetails: {bar: 'bar'}
    };
    const node = shallow(<InstanceHistory {...mockProps} />);

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
      mockProps.activitiesDetails
    );
    expect(InstanceLogNode.prop('selectedLogEntry')).toEqual(
      node.state('selectedLogEntry')
    );
    expect(InstanceLogNode.prop('handleSelectedLogEntry')).toEqual(
      node.instance().handleSelectedLogEntry
    );

    // Instance Events
    const InstanceEventsNode = PaneBodyNode.find(InstanceEvents);
    expect(InstanceEventsNode).toHaveLength(1);
    expect(InstanceEventsNode.prop('instance')).toEqual(mockProps.instance);
    expect(InstanceEventsNode.prop('activitiesDetails')).toEqual(
      mockProps.activitiesDetails
    );
    expect(InstanceEventsNode.prop('selectedLogEntry')).toEqual(
      node.state('selectedLogEntry')
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

  describe('handleSelectedLogEntry', () => {
    it('should set state.selectedLogEntry', () => {
      // given
      const mockProps = {
        instance: {id: 'foo'},
        activitiesDetails: {bar: 'bar'}
      };
      const node = shallow(<InstanceHistory {...mockProps} />);

      // when
      node.instance().handleSelectedLogEntry('foo');
      node.update();

      // then
      expect(node.state('selectedLogEntry')).toBe('foo');
    });
  });
});
