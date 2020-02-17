/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from './CollapsablePanel';

describe('CollapsablePanel', () => {
  it('should render children when expanded', () => {
    // when
    const node = mount(
      <CollapsablePanel
        label="Cool Panel"
        panelPosition={PANEL_POSITION.RIGHT}
        isCollapsed={false}
        toggle={() => {}}
      >
        <div data-test="cool-panel-content">Cool Panel Content</div>
      </CollapsablePanel>
    );

    // then
    const expandedPanel = node.find('[data-test="expanded-panel"]');
    const collapsedPanel = node.find('[data-test="collapsed-panel"]');
    const content = expandedPanel.find('[data-test="cool-panel-content"]');

    expect(expandedPanel).toHaveStyleRule('visibility', 'visible');
    expect(collapsedPanel).toHaveStyleRule('visibility', 'hidden');
    expect(content).toExist();
    expect(content.text()).toContain('Cool Panel Content');
  });

  it('should hide children when collapsed', () => {
    // when
    const node = mount(
      <CollapsablePanel
        label="Cool Panel"
        panelPosition="RIGHT"
        isCollapsed={true}
        toggle={() => {}}
      >
        <div data-test="cool-panel-content">Cool Panel Content</div>
      </CollapsablePanel>
    );

    // then
    const expandedPanel = node.find('[data-test="expanded-panel"]');
    const collapsedPanel = node.find('[data-test="collapsed-panel"]');

    expect(collapsedPanel).toHaveStyleRule('visibility', 'visible');
    expect(expandedPanel).toHaveStyleRule('visibility', 'hidden');
  });

  it('should trigger toggle on button clicks', () => {
    // given
    const toggleMock = jest.fn();

    // when
    const node = mount(
      <CollapsablePanel
        label="Cool Panel"
        panelPosition="RIGHT"
        isCollapsed={false}
        toggle={toggleMock}
      >
        <div data-test="cool-panel-content">Cool Panel Content</div>
      </CollapsablePanel>
    );

    // then
    const collapseButton = node.find('[data-test="collapse-button"]').first();
    const expandButton = node.find('[data-test="expand-button"]').first();

    collapseButton.simulate('click');
    expandButton.simulate('click');

    expect(toggleMock).toHaveBeenCalledTimes(2);
  });
});
