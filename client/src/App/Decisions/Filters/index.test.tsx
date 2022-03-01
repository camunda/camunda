/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';

function getWrapper(initialPath: string = '/') {
  type Props = {
    initialPath?: string;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        {children}
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

const MOCK_FILTERS_PARAMS = {
  name: '3',
  version: '2',
  completed: 'true',
  failed: 'true',
  decisionInstanceId: '123',
  processInstanceId: '456',
  evaluationDate: '789',
} as const;

describe('<Filters />', () => {
  it('should render the correct elements', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByRole('heading', {name: /decision/i})
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/version/i)).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: /instance states/i})
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/completed/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/failed/i)).toBeInTheDocument();
    expect(
      screen.getByLabelText(/decision instance id\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();
  });

  it('should write filters to url', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent('');

    userEvent.selectOptions(screen.getByLabelText(/name/i), ['3']);
    userEvent.selectOptions(screen.getByLabelText(/version/i), ['2']);
    userEvent.click(screen.getByLabelText(/completed/i));
    userEvent.click(screen.getByLabelText(/failed/i));
    userEvent.type(screen.getByLabelText(/decision instance id\(s\)/i), '123');
    userEvent.type(screen.getByLabelText(/process instance id/i), '456');
    userEvent.type(screen.getByLabelText(/evaluation date/i), '789');
    userEvent.click(screen.getByRole('button', {name: /submit/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(
      Object.fromEntries(
        new URLSearchParams(
          screen.getByTestId('search').textContent ?? ''
        ).entries()
      )
    ).toEqual(MOCK_FILTERS_PARAMS);

    userEvent.click(screen.getByRole('button', {name: /reset/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent('');
  });

  it('initialise filter values from url', () => {
    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    expect(screen.getByDisplayValue(/decision 3/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/version 2/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/completed/i)).toBeChecked();
    expect(screen.getByLabelText(/failed/i)).toBeChecked();
    expect(screen.getByDisplayValue(/123/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/456/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/789/i)).toBeInTheDocument();
  });
});
