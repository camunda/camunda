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

  it('should render an exapnding panel and a collapsing one', () => {
    // given
    const node = shallow(<CollapsablePanel {...mockProps} />);

    // then
    // Collapse button
    const CollapseButtonNode = node.find('[data-test="collapse-button"]');
    expect(CollapseButtonNode).toHaveLength(1);
    expect(CollapseButtonNode.prop('onClick')).toBe(
      node.instance().handleButtonClick
    );
    // Expand button
    const ExpandButtonNode = node.find('[data-test="expand-button"]');
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('onClick')).toBe(
      node.instance().handleButtonClick
    );
    expect(node).toMatchSnapshot();
  });
});
