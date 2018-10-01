import React from 'react';
import {shallow} from 'enzyme';

import CollapsablePanel from './CollapsablePanel';
import * as Styled from './styled';

const mockProps = {
  children: <div data-test="children" />,
  collapseButton: <button data-test="collapse-button" />,
  expandButton: <button data-test="expand-button" />,
  maxWidth: 300
};

describe('CollapsablePanel', () => {
  it('should not be collapsed by default', () => {
    // given
    const node = new CollapsablePanel();

    // then
    expect(node.state.isCollapsed).toBe(false);
  });

  it('should render a panel and a collapsebutton', () => {
    // given
    const node = shallow(<CollapsablePanel {...mockProps} />);

    // then
    // Panel Node
    const PanelNode = node.find(Styled.Panel);
    expect(PanelNode).toHaveLength(1);
    expect(PanelNode.prop('isCollapsed')).toBe(false);
    // Collapse button
    const CollapseButtonNode = node.find('[data-test="collapse-button"]');
    expect(CollapseButtonNode).toHaveLength(1);
    expect(CollapseButtonNode.prop('onClick')).toBe(
      node.instance().handleButtonClick
    );
  });

  it('should render expandButton only when panel isCollapsed', () => {
    // ------------------
    // (1) Not Collapsed
    // ------------------

    // given
    const node = shallow(<CollapsablePanel {...mockProps} />);

    // then
    // Expand button should not be rendered
    let ExpandButtonNode = node.find('[data-test="expand-button"]');
    expect(ExpandButtonNode).toHaveLength(0);
    expect(node).toMatchSnapshot();

    // ------------------
    // (2) Collapsed
    // ------------------

    // when
    node.setState({isCollapsed: true});
    node.update();
    ExpandButtonNode = node.find('[data-test="expand-button"]');

    // then
    // Expand button should not be rendered
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('onClick')).toBe(
      node.instance().handleButtonClick
    );
    expect(node).toMatchSnapshot();
  });
});
