/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';
import {ExportButton} from '../ExportButton';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {notificationsStore} from 'modules/stores/notifications';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const Wrapper: React.FC<{
  children?: React.ReactNode;
  initialEntries?: string[];
}> = ({children, initialEntries = ['/processes?active=true']}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
  </QueryClientProvider>
);

const setupBlobMocks = () => {
  // jsdom does not implement createObjectURL/revokeObjectURL.
  const createObjectURL = vi.fn(() => 'blob:mock-url');
  const revokeObjectURL = vi.fn();
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: createObjectURL,
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: revokeObjectURL,
  });
  return {createObjectURL, revokeObjectURL};
};

describe('<ExportButton />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupBlobMocks();
  });

  afterEach(() => {
    vi.resetAllMocks();
    variableFilterStore.reset();
  });

  it('is disabled when totalCount is 0', () => {
    render(<ExportButton totalCount={0} />, {wrapper: Wrapper});
    expect(screen.getByRole('button', {name: /export to csv/i})).toBeDisabled();
  });

  it('is enabled when results exist and triggers a CSV download', async () => {
    let observedRequestBody: unknown;
    mockServer.use(
      http.post('/v2/process-instances/search.csv', async ({request}) => {
        observedRequestBody = await request.json();
        return new HttpResponse('header,row\nvalue,1\n', {
          status: 200,
          headers: {'Content-Type': 'text/csv;charset=UTF-8'},
        });
      }),
    );

    const clickAnchor = vi.fn();
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === 'a') {
        el.click = clickAnchor;
      }
      return el;
    });

    render(<ExportButton totalCount={10} />, {wrapper: Wrapper});

    const button = screen.getByRole('button', {name: /export to csv/i});
    expect(button).toBeEnabled();

    await userEvent.click(button);

    await waitFor(() => expect(clickAnchor).toHaveBeenCalledTimes(1));
    expect(observedRequestBody).toEqual(
      expect.objectContaining({filter: expect.any(Object)}),
    );
    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
  });

  it('shows a truncation notification when the response sets the truncated header', async () => {
    mockServer.use(
      http.post('/v2/process-instances/search.csv', () => {
        return new HttpResponse('header\n', {
          status: 200,
          headers: {
            'Content-Type': 'text/csv;charset=UTF-8',
            'X-Camunda-Export-Truncated': 'true',
          },
        });
      }),
    );

    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === 'a') {
        el.click = vi.fn();
      }
      return el;
    });

    render(<ExportButton totalCount={1_000_000} />, {wrapper: Wrapper});
    await userEvent.click(screen.getByRole('button', {name: /export to csv/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'info',
          title: 'Export was truncated',
        }),
      ),
    );
  });

  it('shows an error notification when the export request fails', async () => {
    mockServer.use(
      http.post('/v2/process-instances/search.csv', () =>
        HttpResponse.json({error: 'boom'}, {status: 500}),
      ),
    );

    render(<ExportButton totalCount={5} />, {wrapper: Wrapper});
    await userEvent.click(screen.getByRole('button', {name: /export to csv/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
        expect.objectContaining({kind: 'error'}),
      ),
    );
  });
});
