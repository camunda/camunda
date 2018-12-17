import React from 'react';
import {shallow} from 'enzyme';

import CollapsablePanel from './CollapsablePanel';

const mockProps = {
  children: <div data-test="children" />,
  collapseButton: <button data-test="collapse-button" />,
  expandButton: <button data-test="expand-button" />,
  maxWidth: 300,
  isCollapsed: false,
  type: 'filters',
  onCollapse: jest.fn()
};

describe.skip('CollapsablePanel', () => {
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

  it('should call onCollapse when clicking the CollapseButton/ExpandButton', () => {
    const node = shallow(<CollapsablePanel {...mockProps} />);
    const CollapseButtonNode = node.find('[data-test="collapse-button"]');

    CollapseButtonNode.simulate('click');
    expect(node.props().onCollapse).toHaveBeenCalled();

    const ExpandButtonNode = node.find('[data-test="expand-button"]');
    ExpandButtonNode.simulate('click');

    expect(node.props().onCollapse).toHaveBeenCalled();
  });
});
