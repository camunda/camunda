/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {TabView} from './index';

describe('TabView', () => {
  it('should render panel header if there is only one tab', () => {
    render(
      <TabView
        tabs={[
          {
            id: 'tab-1',
            label: 'First Tab',
            content: <div>Content of the first tab</div>,
          },
        ]}
      />,
    );

    expect(
      screen.getByRole('heading', {name: 'First Tab'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: 'First Tab'}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Content of the first tab')).toBeInTheDocument();
  });

  it('should render first tab by default', () => {
    render(
      <TabView
        tabs={[
          {
            id: 'tab-1',
            label: 'First Tab',
            content: <div>Content of the first tab</div>,
          },
          {
            id: 'tab-2',
            label: 'Second Tab',
            content: <div>Content of the second tab</div>,
          },
        ]}
      />,
    );

    expect(screen.getByRole('tab', {name: 'First Tab'})).toBeInTheDocument();
    expect(screen.getByRole('tab', {name: 'Second Tab'})).toBeInTheDocument();

    expect(screen.getByText('Content of the first tab')).toBeVisible();
  });

  it('should switch between tabs', async () => {
    const {user} = render(
      <TabView
        tabs={[
          {
            id: 'tab-1',
            label: 'First Tab',
            content: <div>Content of the first tab</div>,
          },
          {
            id: 'tab-2',
            label: 'Second Tab',
            content: <div>Content of the second tab</div>,
          },
        ]}
      />,
    );

    expect(screen.getByText('Content of the first tab')).toBeVisible();

    await user.click(screen.getByRole('tab', {name: 'Second Tab'}));
    expect(screen.queryByText('Content of the first tab')).not.toBeVisible();
    expect(screen.getByText('Content of the second tab')).toBeVisible();

    await user.click(screen.getByRole('tab', {name: 'First Tab'}));
    expect(screen.getByText('Content of the first tab')).toBeVisible();
    expect(screen.queryByText('Content of the second tab')).not.toBeVisible();
  });
});
