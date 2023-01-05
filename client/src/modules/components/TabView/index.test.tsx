/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {TabView} from './index';

describe('TabView', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

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
      {wrapper: ThemeProvider}
    );

    expect(
      screen.getByRole('heading', {name: 'First Tab'})
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'First Tab'})
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
      {wrapper: ThemeProvider}
    );

    expect(screen.getByRole('button', {name: 'First Tab'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Second Tab'})
    ).toBeInTheDocument();

    expect(screen.getByText('Content of the first tab')).toBeInTheDocument();
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
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('Content of the first tab')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Second Tab'}));
    expect(
      screen.queryByText('Content of the first tab')
    ).not.toBeInTheDocument();
    expect(screen.getByText('Content of the second tab')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'First Tab'}));
    expect(screen.getByText('Content of the first tab')).toBeInTheDocument();
    expect(
      screen.queryByText('Content of the second tab')
    ).not.toBeInTheDocument();
  });
});
