import React from 'react';
import {shallow} from 'enzyme';

import {PANE_ID, EXPAND_STATE} from 'modules/constants/splitPane';

import SplitPane from './SplitPane';

describe('SplitPane', () => {
  it('should have by default expandedPaneId null', () => {
    expect(new SplitPane().state.expandedPaneId).toBe(null);
  });

  it('should render children with handleExpand() and paneId props', () => {
    // given
    const FirstChild = () => <div>First Child</div>;
    const SecondChild = () => <div>Second Child</div>;
    const node = shallow(
      <SplitPane>
        <FirstChild />
        <SecondChild />
      </SplitPane>
    );

    // then
    const FirstChildNode = node.find(FirstChild);
    const SecondChildNode = node.find(SecondChild);
    expect(FirstChildNode).toHaveLength(1);
    expect(FirstChildNode.prop('paneId')).toBe(PANE_ID.TOP);
    expect(FirstChildNode.prop('handleExpand')).toBe(
      node.instance().handleExpand
    );
    expect(SecondChildNode).toHaveLength(1);
    expect(SecondChildNode.prop('paneId')).toBe(PANE_ID.BOTTOM);
    expect(SecondChildNode.prop('handleExpand')).toBe(
      node.instance().handleExpand
    );
  });

  describe('paneState', () => {
    it('should render children with proper paneState when TOP pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      );
      node.setState({expandedPaneId: PANE_ID.TOP});
      node.update();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('paneState')).toBe(EXPAND_STATE.EXPANDED);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('paneState')).toBe(EXPAND_STATE.COLLAPSED);
      expect(node).toMatchSnapshot();
    });

    it('should render children with proper paneState when BOTTOM pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      );
      node.setState({expandedPaneId: PANE_ID.BOTTOM});
      node.update();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('paneState')).toBe(EXPAND_STATE.COLLAPSED);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('paneState')).toBe(EXPAND_STATE.EXPANDED);
      expect(node).toMatchSnapshot();
    });

    it('should render children with proper paneState when no pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      );
      node.setState({expandedPaneId: null});
      node.update();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('paneState')).toBe(EXPAND_STATE.DEFAULT);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('paneState')).toBe(EXPAND_STATE.DEFAULT);
      expect(node).toMatchSnapshot();
    });
  });
});
