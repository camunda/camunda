/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {PageErrorBoundary} from './index';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';

const ThrowRouteError: React.FC<{error: unknown}> = ({error}) => {
  throw error;
};

const createTestRouter = (error?: unknown) =>
  createMemoryRouter(
    [
      {
        path: '/',
        element: error ? <ThrowRouteError error={error} /> : <div />,
        errorElement: <PageErrorBoundary />,
      },
    ],
    {
      initialEntries: ['/'],
    },
  );

const Wrapper: React.FC<{error?: unknown}> = ({error}) => {
  const router = createTestRouter(error);
  return <RouterProvider router={router} />;
};

describe('PageErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    window.addEventListener('error', (e) => {
      e.preventDefault();
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should display error message and reload CTA for route error response', () => {
    const error = {
      status: 404,
      statusText: 'Not Found',
      data: 'The requested page was not found.',
    };

    render(null, {
      wrapper: (props) => <Wrapper {...props} error={error} />,
    });

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: /reload the page/i}),
    ).toBeInTheDocument();
  });

  it('should display error message and reload CTA for server errors', () => {
    const error = {
      status: 500,
      statusText: 'Internal Server Error',
      data: '',
    };

    render(null, {
      wrapper: (props) => <Wrapper {...props} error={error} />,
    });

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: /reload the page/i}),
    ).toBeInTheDocument();
  });

  it('should display error message and stack trace for Error instances', () => {
    const error = new Error('Something exploded');
    error.stack = 'Error: Something exploded\n    at Component.render';

    render(null, {
      wrapper: (props) => <Wrapper {...props} error={error} />,
    });

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Something exploded')).toBeInTheDocument();
    expect(screen.getByText('Error stack trace')).toBeInTheDocument();
    expect(
      screen.getByText((content) =>
        content.includes('Error: Something exploded'),
      ),
    ).toBeInTheDocument();
  });

  it('should display fallback message when stack trace is unavailable', () => {
    const error = new Error('No stack');
    error.stack = undefined;

    render(null, {
      wrapper: (props) => <Wrapper {...props} error={error} />,
    });

    expect(screen.getByText('No stack trace available')).toBeInTheDocument();
  });

  it('should display generic message for unknown error types', () => {
    const error = {unexpected: true};

    render(null, {
      wrapper: (props) => <Wrapper {...props} error={error} />,
    });

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Unknown error')).toBeInTheDocument();
  });
});
