/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {renderHook, waitFor} from 'modules/testing/testing-library';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {nodeMockServer} from 'modules/testing/nodeMockServer';
import {useProcessDefinitionXml} from './useProcessDefinitionXml.query';

function renderWithClient(processDefinitionKey: string) {
  const queryClient = new QueryClient();
  return renderHook(() => useProcessDefinitionXml(processDefinitionKey), {
    wrapper: ({children}) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe('useProcessDefinitionXml', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it.each([403, 404])(
    'should not retry when the response is %i',
    async (status) => {
      let requestCount = 0;
      nodeMockServer.use(
        http.get('/v2/process-definitions/:key/xml', () => {
          requestCount++;
          return new HttpResponse('', {status});
        }),
      );

      const {result} = renderWithClient('process-definition-key');

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(requestCount).toBe(1);
    },
  );

  it('should still retry up to 3 times for a transient error', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    let requestCount = 0;
    nodeMockServer.use(
      http.get('/v2/process-definitions/:key/xml', () => {
        requestCount++;
        return new HttpResponse('', {status: 500});
      }),
    );

    const {result} = renderWithClient('process-definition-key');

    await vi.advanceTimersByTimeAsync(1000 + 2000 + 4000);
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(requestCount).toBe(4);
  });
});
