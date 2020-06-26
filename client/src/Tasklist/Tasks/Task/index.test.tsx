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

const historyMock = createMemoryHistory();
const Wrapper: React.FC = ({children}) => (
  <Router history={historyMock}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </Router>
);

describe('<Task />', () => {
  it('should render task', () => {
    render(
      <Task
        taskId="1"
        name="name"
        workflowName="workflowName"
        creationTime="2020-05-29 14:00:00"
        assignee={{firstname: 'Demo', lastname: 'user', username: 'Demouser'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('workflowName')).toBeInTheDocument();
    expect(screen.getByText('2020-05-29 14:00:00')).toBeInTheDocument();
    expect(screen.getByText('Demo user')).toBeInTheDocument();
  });

  it('should render -- as assignee if task is not assigned', () => {
    render(
      <Task
        taskId="1"
        name="name"
        workflowName="workflowName"
        creationTime="2020-05-29 14:00:00"
        assignee={null}
      />,
      {
        wrapper: Wrapper,
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
        workflowName="workflowName"
        creationTime="invalid date"
        assignee={{firstname: 'Demo', lastname: 'user', username: 'Demouser'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('creation-time')).toBeEmptyDOMElement();
    global.console.error = originalConsoleError;
  });

  it('should navigate to task detail on click', () => {
    render(
      <Task
        taskId="1"
        name="name"
        workflowName="workflowName"
        creationTime="2020-05-29 14:00:00"
        assignee={{firstname: 'Demo', lastname: 'user', username: 'Demouser'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    fireEvent.click(screen.getByText('workflowName'));
    expect(historyMock.location.pathname).toBe('/1');
  });
});
