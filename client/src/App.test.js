import React from 'react';
import {mount} from 'enzyme';

import App from './App';

import {MemoryRouter} from 'react-router';

jest.mock('react-router-dom', () => {
  const RealThing = require.requireActual('react-router-dom');
  return {
    ...RealThing,
    BrowserRouter: ({children}) => <div>{children}</div>
  };
});

jest.mock('components', () => {
  return {
    ErrorBoundary: ({children}) => <div>{children}</div>
  };
});

jest.mock('./components', () => {
  const {Route} = require.requireActual('react-router-dom');
  return {
    PrivateRoute: Route,
    Header: () => <p>Header</p>,
    Footer: () => <p>Footer</p>,
    Login: () => <p>Login</p>,
    Home: () => <p>Home</p>,
    Dashboards: () => <p>Dashboards</p>,
    Reports: () => <p>Reports</p>,
    Report: () => <p>Report</p>,
    Dashboard: () => <p>Dashboard</p>,
    Analysis: () => <p>Analysis</p>,
    Alerts: () => <p>Alerts</p>,
    Sharing: () => <p>Sharing</p>
  };
});

it('should include a header for the Home page', () => {
  const node = mount(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>
  );

  expect(node).toIncludeText('Home');
  expect(node).toIncludeText('Header');
  expect(node).toIncludeText('Footer');
});

it('should not include a header for the login page', () => {
  const node = mount(
    <MemoryRouter initialEntries={['/login']}>
      <App />
    </MemoryRouter>
  );

  expect(node).toIncludeText('Login');
  expect(node).not.toIncludeText('Header');
  expect(node).not.toIncludeText('Footer');
});

it('should not include a header for shared resources', () => {
  const node = mount(
    <MemoryRouter initialEntries={['/share/report/3']}>
      <App />
    </MemoryRouter>
  );

  expect(node).toIncludeText('Sharing');
  expect(node).not.toIncludeText('Header');
  expect(node).not.toIncludeText('Footer');
});
