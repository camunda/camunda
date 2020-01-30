/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import CollapsablePanel from './CollapsablePanel';

const mockDefaultProps = {
  onCollapse: jest.fn(),
  expandButton: <button data-test="expand-button" />,
  collapseButton: <button data-test="collapse-button" />,
  maxWidth: 300
};

const CollapsablePanelHeader = () => <div>Header Content</div>;
const CollapsablePanelBody = () => <div>Body Content</div>;

const mountNode = mockCustomProps => {
  return mount(
    <ThemeProvider>
      <CollapsablePanel {...mockDefaultProps} {...mockCustomProps}>
        <CollapsablePanelHeader />
        <CollapsablePanelBody />
      </CollapsablePanel>
    </ThemeProvider>
  );
};

describe.skip('CollapsablePanel', () => {
  let node;
  let CollapsablePanelNode;
  beforeEach(() => {
    node = mountNode();
    CollapsablePanelNode = node.find(CollapsablePanel);
  });

  it('should render Panel in default state - expanded', () => {
    expect(CollapsablePanelNode.instance().props.isCollapsed).toBe(false);
  });

  it('should render the CollapseButton/ExpandButton', () => {
    // then
    // Collapse button
    const CollapseButtonNode = CollapsablePanelNode.find(
      '[data-test="collapse-button"]'
    );
    expect(CollapseButtonNode).toHaveLength(1);
    expect(CollapseButtonNode.prop('onClick')).toBe(
      CollapsablePanelNode.instance().handleButtonClick
    );
    // Expand button
    const ExpandButtonNode = CollapsablePanelNode.find(
      '[data-test="expand-button"]'
    );
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('onClick')).toBe(
      CollapsablePanelNode.instance().handleButtonClick
    );
  });

  it('should call onCollapse when clicking the CollapseButton/ExpandButton', () => {
    const CollapseButtonNode = node.find('[data-test="collapse-button"]');

    CollapseButtonNode.simulate('click');
    expect(CollapsablePanelNode.props().onCollapse).toHaveBeenCalled();

    const ExpandButtonNode = node.find('[data-test="expand-button"]');
    ExpandButtonNode.simulate('click');

    expect(CollapsablePanelNode.props().onCollapse).toHaveBeenCalled();
  });
});
