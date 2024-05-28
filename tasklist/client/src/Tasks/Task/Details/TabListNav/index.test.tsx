/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {TabListNav} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="*" element={children} />
      </Routes>
      <LocationLog />
    </MemoryRouter>
  );

  return Wrapper;
};

const MOCK_TABS = [
  {
    key: 'key',
    title: 'A tab',
    label: 'A tab',
    selected: true,
    to: {
      pathname: '/first',
    },
  },
  {
    key: 'key2',
    title: 'Another tab',
    label: 'Another tab',
    selected: false,
    to: {
      pathname: '/second',
    },
  },
] satisfies React.ComponentProps<typeof TabListNav>['items'];

describe('<TabListNav />', () => {
  it('should render tabs', () => {
    render(<TabListNav label="A tab list" items={MOCK_TABS} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByLabelText(/a tab list/i)).toBeVisible();
    expect(screen.getByRole('link', {name: /a tab/i})).toBeVisible();
    expect(screen.getByRole('link', {name: /another tab/i})).toBeVisible();
  });

  it('should preserve the URL on tab change', async () => {
    const {user} = render(<TabListNav label="A tab list" items={MOCK_TABS} />, {
      wrapper: getWrapper(['/?foo=bar']),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent('?foo=bar');

    await user.click(screen.getByRole('link', {name: /another tab/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/second');
    expect(screen.getByTestId('search')).toHaveTextContent('?foo=bar');
  });
});
