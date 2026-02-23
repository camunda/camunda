/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CopyVariablesButton} from './CopyVariablesButton';
import {render, screen, waitFor} from 'modules/testing-library';
import {createVariable, createProcessInstance} from 'modules/testUtils';
import type {QueryVariablesResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockServer} from 'modules/mock-server/node';

import {writeToClipboard} from './writeToClipboard';
import {http, HttpResponse} from 'msw';

vi.mock('./writeToClipboard', () => ({
  writeToClipboard: vi.fn(),
}));
const mockWriteToClipboard = vi.mocked(writeToClipboard);

const createWrapper = (processInstanceId = '123456') => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[Paths.processInstance(processInstanceId)]}>
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('CopyVariableButton', () => {
  it('should be disabled (no variables)', () => {
    const emptyResponse: QueryVariablesResponseBody = {
      items: [],
      page: {
        totalItems: 0,
      },
    };

    mockServer.use(
      http.get(
        '/v2/process-instances/:processInstanceKey',
        () => {
          return HttpResponse.json(
            createProcessInstance({processInstanceKey: '123456'}),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/variables/search',
        () => {
          return HttpResponse.json(emptyResponse);
        },
        {once: true},
      ),
    );

    render(<CopyVariablesButton />, {wrapper: createWrapper()});

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled (too many variables)', () => {
    const variables = [...Array(50)].map((_, i) =>
      createVariable({
        name: i.toString(),
        value: 'value',
      }),
    );

    const response: QueryVariablesResponseBody = {
      items: variables,
      page: {
        totalItems: 100,
      },
    };

    mockServer.use(
      http.get(
        '/v2/process-instances/:processInstanceKey',
        () => {
          return HttpResponse.json(
            createProcessInstance({processInstanceKey: '123456'}),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/variables/search',
        () => {
          return HttpResponse.json(response);
        },
        {once: true},
      ),
    );

    render(<CopyVariablesButton />, {wrapper: createWrapper()});

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled (truncated values)', () => {
    const response: QueryVariablesResponseBody = {
      items: [createVariable({isTruncated: true})],
      page: {
        totalItems: 1,
      },
    };

    mockServer.use(
      http.get(
        '/v2/process-instances/:processInstanceKey',
        () => {
          return HttpResponse.json(
            createProcessInstance({processInstanceKey: '123456'}),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/variables/search',
        () => {
          return HttpResponse.json(response);
        },
        {once: true},
      ),
    );

    render(<CopyVariablesButton />, {wrapper: createWrapper()});

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should copy variables to clipboard', async () => {
    const response: QueryVariablesResponseBody = {
      items: [
        createVariable({
          name: 'jsonVariable',
          value: JSON.stringify({a: 123, b: [1, 2, 3], c: 'text'}),
        }),
        createVariable({
          name: 'numberVariable',
          value: '666',
        }),
        createVariable({
          name: 'stringVariable',
          value: '"text"',
        }),
      ],
      page: {
        totalItems: 3,
      },
    };

    mockServer.use(
      http.get(
        '/v2/process-instances/:processInstanceKey',
        () => {
          return HttpResponse.json(
            createProcessInstance({processInstanceKey: '123456'}),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/variables/search',
        () => {
          return HttpResponse.json(response);
        },
        {once: true},
      ),
    );

    const {user} = render(<CopyVariablesButton />, {wrapper: createWrapper()});

    await waitFor(() => {
      expect(
        screen.getByRole('button', {
          name: 'Copy variables',
        }),
      ).toBeEnabled();
    });
    await user.click(
      screen.getByRole('button', {
        name: 'Copy variables',
      }),
    );

    expect(mockWriteToClipboard).toHaveBeenCalledWith(
      '{"jsonVariable":{"a":123,"b":[1,2,3],"c":"text"},"numberVariable":666,"stringVariable":"text"}',
    );
  });
});
