/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from './index';

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
    const expandedPanel = node.find('[data-test="expanded-panel"]').first();
    const collapsedPanel = node.find('[data-test="collapsed-panel"]').first();
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
    const expandedPanel = node.find('[data-test="expanded-panel"]').first();
    const collapsedPanel = node.find('[data-test="collapsed-panel"]').first();

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

it('should have background color style rule when hasBackgroundColor is true', () => {
  // when
  const node = mount(
    <CollapsablePanel
      label="Cool Panel"
      hasBackgroundColor
      panelPosition="RIGHT"
      isCollapsed={false}
      toggle={() => {}}
    >
      <div data-test="cool-panel-content">Cool Panel Content</div>
    </CollapsablePanel>
  );

  // then
  const expandedPanel = node.find('[data-test="expanded-panel"]').first();
  expect(expandedPanel).toHaveStyleRule('background-color', '#313238');
});

it('should have border-right rule when panel position is RIGHT', () => {
  // when
  const node = mount(
    <CollapsablePanel
      label="Cool Panel"
      panelPosition="RIGHT"
      isCollapsed={false}
      toggle={() => {}}
    >
      <div data-test="cool-panel-content">Cool Panel Content</div>
    </CollapsablePanel>
  );

  // then
  const expandedPanel = node.find('[data-test="expanded-panel"]').first();
  expect(expandedPanel).toHaveStyleRule('border-right', 'none');
});
it('should not have border-right rule when panel position is not RIGHT', () => {
  // when
  const node = mount(
    <CollapsablePanel
      label="Cool Panel"
      panelPosition="LEFT"
      isCollapsed={false}
      toggle={() => {}}
    >
      <div data-test="cool-panel-content">Cool Panel Content</div>
    </CollapsablePanel>
  );

  // then
  const expandedPanel = node.find('[data-test="expanded-panel"]').first();
  expect(expandedPanel).not.toHaveStyleRule('border-right', 'none');
});
