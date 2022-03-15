/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {decisionInstancesVisibleFiltersStore} from 'modules/stores/decisionInstancesVisibleFilters';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from './index';

function getWrapper(initialPath: string = '/decisions') {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

const MOCK_FILTERS_PARAMS = {
  name: '3',
  version: '2',
  evaluated: 'true',
  failed: 'true',
  decisionInstanceIds: '123',
  processInstanceId: '456',
  evaluationDate: '789',
} as const;

describe('<Filters />', () => {
  beforeEach(() => {
    decisionInstancesVisibleFiltersStore.reset();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render the correct elements', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(/^decision$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/version/i)).toBeInTheDocument();
    expect(screen.getByText(/^instance states$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluated/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/failed/i)).toBeInTheDocument();
    expect(screen.getByText(/^more filters$/i)).toBeInTheDocument();
    expect(
      screen.queryByLabelText(/decision instance id\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance id/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();
  });

  it('should write filters to url', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent('');

    userEvent.selectOptions(screen.getByLabelText(/name/i), ['3']);

    userEvent.selectOptions(screen.getByLabelText(/version/i), ['2']);

    userEvent.click(screen.getByLabelText(/evaluated/i));

    userEvent.click(screen.getByLabelText(/failed/i));

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/decision instance id\(s\)/i));
    userEvent.paste(screen.getByLabelText(/decision instance id\(s\)/i), '123');

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/process instance id/i));
    userEvent.paste(screen.getByLabelText(/process instance id/i), '456');

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/evaluation date/i));
    userEvent.paste(screen.getByLabelText(/evaluation date/i), '789');

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(
            screen.getByTestId('search').textContent ?? ''
          ).entries()
        )
      ).toEqual(MOCK_FILTERS_PARAMS)
    );

    userEvent.click(screen.getByRole('button', {name: /reset/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?evaluated=true&failed=true'
    );
  });

  it('initialise filter values from url', () => {
    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    expect(screen.getByDisplayValue(/decision 3/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/version 2/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluated/i)).toBeChecked();
    expect(screen.getByLabelText(/failed/i)).toBeChecked();
    expect(screen.getByDisplayValue(/123/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/456/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/789/i)).toBeInTheDocument();
  });

  it('should persist enabled filters on session', () => {
    const {unmount} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByLabelText(/decision instance id\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance id/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/decision instance id\(s\)/i));

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/process instance id/i));

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/evaluation date/i));

    unmount();

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByLabelText(/decision instance id\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();
  });

  it('should persist enabled filters from URL on session', () => {
    const {unmount} = render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    expect(
      screen.getByLabelText(/decision instance id\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();

    unmount();

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByLabelText(/decision instance id\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();
  });

  it('should hide optional filters', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/decision instance id\(s\)/i));

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/process instance id/i));

    userEvent.click(screen.getByText(/^more filters$/i));
    userEvent.click(screen.getByText(/evaluation date/i));

    expect(
      screen.getByLabelText(/decision instance id\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();

    userEvent.click(screen.getByTestId('delete-decisionInstanceIds'));
    userEvent.click(screen.getByTestId('delete-processInstanceId'));
    userEvent.click(screen.getByTestId('delete-evaluationDate'));

    expect(
      screen.queryByLabelText(/decision instance id\(s\)/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/process instance id/i)
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/evaluation date/i)).not.toBeInTheDocument();
  });
});
