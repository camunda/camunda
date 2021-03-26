/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {Task} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {currentUser} from 'modules/mock-schema/mocks/current-user';

const createWrapper = (history = createMemoryHistory()) => {
  const Wrapper: React.FC = ({children}) => (
    <Router history={history}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </Router>
  );

  return Wrapper;
};

describe('<Task />', () => {
  it('should render task', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29 14:00:00"
        assignee={currentUser}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('processName')).toBeInTheDocument();
    expect(screen.getByText('2020-05-29 14:00:00')).toBeInTheDocument();
    expect(screen.getByText('Demo User')).toBeInTheDocument();
  });

  it('should render -- as assignee if task is not assigned', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29 14:00:00"
        assignee={null}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTestId('assignee')).toHaveTextContent('--');
  });

  it('should render creation time as empty value if given date is invalid', () => {
    const originalConsoleError = global.console.error;
    global.console.error = jest.fn();
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="invalid date"
        assignee={currentUser}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTestId('creation-time')).toBeEmptyDOMElement();
    global.console.error = originalConsoleError;
  });

  it('should navigate to task detail on click', () => {
    const historyMock = createMemoryHistory();
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29 14:00:00"
        assignee={currentUser}
      />,
      {
        wrapper: createWrapper(historyMock),
      },
    );

    fireEvent.click(screen.getByText('processName'));
    expect(historyMock.location.pathname).toBe('/1');
  });

  it('should preserve search params', () => {
    const mockSearchParams = '?filter=all-open';
    const historyMock = createMemoryHistory({
      initialEntries: [mockSearchParams],
    });
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29 14:00:00"
        assignee={currentUser}
      />,
      {
        wrapper: createWrapper(historyMock),
      },
    );

    fireEvent.click(screen.getByText('processName'));

    expect(historyMock.location.pathname).toBe('/1');
    expect(historyMock.location.search).toBe(mockSearchParams);
  });
});
