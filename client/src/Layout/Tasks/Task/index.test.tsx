/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, fireEvent} from '@testing-library/react';
import {Task} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {LocationLog} from 'modules/utils/LocationLog';

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MockThemeProvider>
      <MemoryRouter initialEntries={initialEntries}>
        {children}
        <LocationLog />
      </MemoryRouter>
    </MockThemeProvider>
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
        assignee={currentUser.userId}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('processName')).toBeInTheDocument();
    expect(screen.getByText('2020-05-29 14:00:00')).toBeInTheDocument();
    expect(screen.getByText('demo')).toBeInTheDocument();
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
        assignee={currentUser.userId}
      />,
      {
        wrapper: createWrapper(),
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
        processName="processName"
        creationTime="2020-05-29 14:00:00"
        assignee={currentUser.userId}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    fireEvent.click(screen.getByText('processName'));
    expect(screen.getByTestId('pathname')).toHaveTextContent('/1');
  });

  it('should preserve search params', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationTime="2020-05-29 14:00:00"
        assignee={currentUser.userId}
      />,
      {
        wrapper: createWrapper(['/?filter=all-open']),
      },
    );

    fireEvent.click(screen.getByText('processName'));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/1');
    expect(screen.getByTestId('search')).toHaveTextContent('filter=all-open');
  });
});
