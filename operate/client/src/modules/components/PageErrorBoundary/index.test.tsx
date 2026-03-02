/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {PageErrorBoundary} from './index';

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useRouteError: vi.fn(),
    isRouteErrorResponse: vi.fn(),
  };
});

import {useRouteError, isRouteErrorResponse} from 'react-router-dom';

const mockUseRouteError = vi.mocked(useRouteError);
const mockIsRouteErrorResponse = vi.mocked(isRouteErrorResponse);

describe('PageErrorBoundary', () => {
  describe('route error response', () => {
    it('should render status, statusText, and data when error is a route error response', () => {
      mockUseRouteError.mockReturnValue({
        status: 404,
        statusText: 'Not Found',
        data: 'The requested page was not found.',
      });
      mockIsRouteErrorResponse.mockReturnValue(true);

      render(<PageErrorBoundary />);

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      expect(screen.getByText('404 Not Found')).toBeInTheDocument();
      expect(
        screen.getByText('The requested page was not found.'),
      ).toBeInTheDocument();
    });

    it('should render reload CTA for route error response', () => {
      mockUseRouteError.mockReturnValue({
        status: 500,
        statusText: 'Internal Server Error',
        data: '',
      });
      mockIsRouteErrorResponse.mockReturnValue(true);

      render(<PageErrorBoundary />);

      expect(
        screen.getByRole('link', {name: /reload the page/i}),
      ).toBeInTheDocument();
    });
  });

  describe('Error instance', () => {
    it('should render error message and stack trace when error is an Error', () => {
      const error = new Error('Something exploded');
      error.stack = 'Error: Something exploded\n    at Component.render';
      mockUseRouteError.mockReturnValue(error);
      mockIsRouteErrorResponse.mockReturnValue(false);

      render(<PageErrorBoundary />);

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      expect(screen.getByText('Something exploded')).toBeInTheDocument();
      expect(screen.getByText('Error stack trace')).toBeInTheDocument();
      expect(
        screen.getByText((content) =>
          content.includes('Error: Something exploded'),
        ),
      ).toBeInTheDocument();
    });

    it('should show fallback stack trace message when stack is undefined', () => {
      const error = new Error('No stack');
      error.stack = undefined;
      mockUseRouteError.mockReturnValue(error);
      mockIsRouteErrorResponse.mockReturnValue(false);

      render(<PageErrorBoundary />);

      expect(screen.getByText('No stack trace available')).toBeInTheDocument();
    });

    it('should render reload CTA for Error instance', () => {
      mockUseRouteError.mockReturnValue(new Error('crash'));
      mockIsRouteErrorResponse.mockReturnValue(false);

      render(<PageErrorBoundary />);

      expect(
        screen.getByRole('link', {name: /reload the page/i}),
      ).toBeInTheDocument();
    });
  });

  describe('unknown error', () => {
    it('should render "Unknown error" for an unrecognized error shape', () => {
      mockUseRouteError.mockReturnValue({unexpected: true});
      mockIsRouteErrorResponse.mockReturnValue(false);

      render(<PageErrorBoundary />);

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      expect(screen.getByText('Unknown error')).toBeInTheDocument();
    });

    it('should render reload CTA for unknown error', () => {
      mockUseRouteError.mockReturnValue(null);
      mockIsRouteErrorResponse.mockReturnValue(false);

      render(<PageErrorBoundary />);

      expect(
        screen.getByRole('link', {name: /reload the page/i}),
      ).toBeInTheDocument();
    });
  });
});
