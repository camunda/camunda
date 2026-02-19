/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {
  renderHook,
  waitFor,
  act,
  type RenderHookResult,
} from 'modules/testing-library';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {
  endpoints,
  type GetIncidentProcessInstanceStatisticsByErrorRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {useIncidentProcessInstanceStatisticsByErrorPaginated} from './useIncidentProcessInstanceStatisticsByErrorPaginated';

const wrapper = ({children}: {children: React.ReactNode}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

type HookData = {
  items: Array<{errorHashCode: number; errorMessage: string}>;
  totalCount: number;
};

const createIncidentItem = (index: number) => ({
  errorHashCode: index + 1,
  errorMessage: `error-${index + 1}`,
  activeInstancesWithErrorCount: 100 - index,
});

const renderPaginatedHook = (): RenderHookResult<
  ReturnType<
    typeof useIncidentProcessInstanceStatisticsByErrorPaginated<HookData>
  >,
  unknown
> => {
  return renderHook(
    () =>
      useIncidentProcessInstanceStatisticsByErrorPaginated<HookData>({
        enablePeriodicRefetch: false,
        select: (data) => ({
          items: data.pages.flatMap((page) => page.items),
          totalCount: data.pages[0]?.page.totalItems ?? 0,
        }),
      }),
    {wrapper},
  );
};

describe('useIncidentProcessInstanceStatisticsByErrorPaginated', () => {
  it('should fetch second page using offset-based pagination and append remaining items', async () => {
    const allItems = Array.from({length: 61}, (_, index) =>
      createIncidentItem(index),
    );
    const requestPages: Array<{from?: number; limit?: number}> = [];

    mockServer.use(
      http.post(
        endpoints.getIncidentProcessInstanceStatisticsByError.getUrl(),
        async ({request}) => {
          const body =
            (await request.json()) as GetIncidentProcessInstanceStatisticsByErrorRequestBody;
          const from = body.page?.from;
          const limit = body.page?.limit;

          requestPages.push({from, limit});

          if (from === 0 && limit === 50) {
            return HttpResponse.json({
              items: allItems.slice(0, 50),
              page: {totalItems: 61, hasMoreTotalItems: false},
            });
          }

          if (from === 50 && limit === 50) {
            return HttpResponse.json({
              items: allItems.slice(50),
              page: {totalItems: 61, hasMoreTotalItems: false},
            });
          }

          return HttpResponse.json(
            {
              items: [],
              page: {totalItems: 61, hasMoreTotalItems: false},
            },
            {status: 400},
          );
        },
      ),
    );

    const {result} = renderPaginatedHook();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.items).toHaveLength(50);
    expect(result.current.data?.totalCount).toBe(61);

    await act(async () => {
      await result.current.fetchNextPage();
    });

    await waitFor(() => expect(result.current.data?.items).toHaveLength(61));

    expect(result.current.data?.items.at(0)?.errorHashCode).toBe(1);
    expect(result.current.data?.items.at(-1)?.errorHashCode).toBe(61);
    expect(requestPages).toEqual([
      {from: 0, limit: 50},
      {from: 50, limit: 50},
    ]);
  });
});
