import React from 'react';
import {shallow} from 'enzyme';

import SplitPane from './SplitPane';
import {PANE_ID, PANE_STATE} from './Pane/constants';

describe('SplitPane', () => {
  it('should have by default expandedPaneId null', () => {
    expect(new SplitPane().state.expandedPaneId).toBe(null);
  });

  it('should render children with expand() and paneId props', () => {
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
    expect(FirstChildNode.prop('expand')).toBe(node.instance().expand);
    expect(SecondChildNode).toHaveLength(1);
    expect(SecondChildNode.prop('paneId')).toBe(PANE_ID.BOTTOM);
    expect(SecondChildNode.prop('expand')).toBe(node.instance().expand);
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
      expect(FirstChildNode.prop('paneState')).toBe(PANE_STATE.EXPANDED);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('paneState')).toBe(PANE_STATE.COLLAPSED);
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
      expect(FirstChildNode.prop('paneState')).toBe(PANE_STATE.COLLAPSED);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('paneState')).toBe(PANE_STATE.EXPANDED);
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
      expect(FirstChildNode.prop('paneState')).toBe(PANE_STATE.DEFAULT);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('paneState')).toBe(PANE_STATE.DEFAULT);
      expect(node).toMatchSnapshot();
    });
  });
});
