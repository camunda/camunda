/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {fireEvent, render, screen} from '@testing-library/react';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('CollapsablePanel', () => {
  it('should render children when expanded', () => {
    render(
      <CollapsablePanel
        label="Cool Panel"
        panelPosition={PANEL_POSITION.RIGHT}
        isCollapsed={false}
        toggle={() => {}}
      >
        <div data-testid="cool-panel-content">Cool Panel Content</div>
      </CollapsablePanel>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByTestId('expanded-panel')).toHaveStyleRule(
      'visibility',
      'visible'
    );
    expect(screen.getByTestId('collapsed-panel')).toHaveStyleRule(
      'visibility',
      'hidden'
    );
  });

  it('should hide children when collapsed', () => {
    render(
      <CollapsablePanel
        label="Cool Panel"
        panelPosition="RIGHT"
        isCollapsed={true}
        toggle={() => {}}
      >
        <div>Cool Panel Content</div>
      </CollapsablePanel>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByTestId('collapsed-panel')).toHaveStyleRule(
      'visibility',
      'visible'
    );
    expect(screen.getByTestId('expanded-panel')).toHaveStyleRule(
      'visibility',
      'hidden'
    );
  });

  it('should trigger toggle on button clicks', () => {
    const toggleMock = jest.fn();

    render(
      <CollapsablePanel
        label="Cool Panel"
        panelPosition="RIGHT"
        isCollapsed={false}
        toggle={toggleMock}
      >
        <div data-testid="cool-panel-content">Cool Panel Content</div>
      </CollapsablePanel>,
      {wrapper: ThemeProvider}
    );

    fireEvent.click(screen.getByTestId('collapse-button'));
    fireEvent.click(screen.getByTestId('expand-button'));

    expect(toggleMock).toHaveBeenCalledTimes(2);
  });
});

it('should have background color style rule when hasBackgroundColor is true', () => {
  render(
    <CollapsablePanel
      label="Cool Panel"
      hasBackgroundColor
      panelPosition="RIGHT"
      isCollapsed={false}
      toggle={() => {}}
    >
      <div data-testid="cool-panel-content">Cool Panel Content</div>
    </CollapsablePanel>,
    {wrapper: ThemeProvider}
  );

  expect(screen.getByTestId('expanded-panel')).toHaveStyleRule(
    'background-color',
    '#f7f8fa'
  );
});

it('should have border-right rule when panel position is RIGHT', () => {
  render(
    <CollapsablePanel
      label="Cool Panel"
      panelPosition="RIGHT"
      isCollapsed={false}
      toggle={() => {}}
    >
      <div data-testid="cool-panel-content">Cool Panel Content</div>
    </CollapsablePanel>,
    {wrapper: ThemeProvider}
  );

  expect(screen.getByTestId('expanded-panel')).toHaveStyleRule(
    'border-right',
    'none'
  );
});

it('should not have border-right rule when panel position is not RIGHT', () => {
  render(
    <CollapsablePanel
      label="Cool Panel"
      panelPosition="LEFT"
      isCollapsed={false}
      toggle={() => {}}
    >
      <div data-testid="cool-panel-content">Cool Panel Content</div>
    </CollapsablePanel>,
    {wrapper: ThemeProvider}
  );

  expect(screen.getByTestId('expanded-panel')).not.toHaveStyleRule(
    'border-right',
    'none'
  );
});
