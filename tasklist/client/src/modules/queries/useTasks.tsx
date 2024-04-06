/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  InfiniteData,
  UseInfiniteQueryOptions,
  useInfiniteQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {Task} from 'modules/types';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {TaskFilters} from 'modules/hooks/useTaskFilters';
import chunk from 'lodash/chunk';
import {useCurrentUser} from './useCurrentUser';

const POLLING_INTERVAL = 5000;
const MAX_TASKS_DISPLAYED = 200;
const MAX_TASKS_PER_REQUEST = 50;
const MAX_PAGE_COUNT = Math.ceil(MAX_TASKS_DISPLAYED / MAX_TASKS_PER_REQUEST);

type PageParam = {
  pageParam?:
    | {
        searchBefore: [string, string];
      }
    | {
        searchAfter: [string, string];
      };
};

function getQueryKey(keys: unknown[]) {
  return ['tasks', ...keys];
}

function useTasks(
  filters: TaskFilters,
  options?: Partial<
    Pick<
      UseInfiniteQueryOptions<Task[], RequestError | Error>,
      'refetchInterval'
    >
  >,
) {
  const {refetchInterval} = options ?? {};
  const {data: currentUser} = useCurrentUser();
  const payload = getQueryVariables(filters, {
    assignee: currentUser?.userId,
    pageSize: MAX_TASKS_PER_REQUEST,
  });
  const client = useQueryClient();
  const result = useInfiniteQuery<Task[], RequestError | Error, Task[]>({
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async ({pageParam}: PageParam) => {
      const {response, error} = await request(
        api.searchTasks({...payload, ...pageParam}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch tasks');
    },
    keepPreviousData: true,
    refetchInterval: refetchInterval ?? POLLING_INTERVAL,
    getNextPageParam: (lastPage) => {
      if (lastPage.length < MAX_TASKS_PER_REQUEST) {
        return undefined;
      }

      const lastTask = lastPage[lastPage.length - 1];

      return {
        searchAfter: lastTask.sortValues,
      };
    },
    getPreviousPageParam: (firstPage) => {
      if (firstPage.length < MAX_TASKS_PER_REQUEST) {
        return undefined;
      }

      const firstTask = firstPage[0];

      if (firstTask.isFirst) {
        return undefined;
      }

      return {
        searchBefore: firstTask.sortValues,
      };
    },
  });

  async function fetchPreviousTasks() {
    if (!result.hasPreviousPage) {
      return [];
    }

    const {data} = await result.fetchPreviousPage();
    function calculatePreviousTasksData(
      data?: InfiniteData<Task[]>,
    ): InfiniteData<Task[]> | undefined {
      const tasks = data?.pages.flat() ?? [];
      if (data === undefined || tasks.length <= MAX_TASKS_DISPLAYED) {
        return data;
      }
      const trimmedTasks = tasks.slice(0, MAX_TASKS_DISPLAYED + 1);
      const lastTask = trimmedTasks.pop();

      return {
        pages: chunk(trimmedTasks, MAX_TASKS_PER_REQUEST),
        pageParams: [
          ...data.pageParams.slice(0, MAX_PAGE_COUNT - 1),
          {
            searchBefore: lastTask!.sortValues,
          },
        ],
      };
    }
    const newData = calculatePreviousTasksData(data);

    client.setQueryData<InfiniteData<Task[]>>(
      getQueryKey(Object.values(payload)),
      () => {
        return newData;
      },
    );

    return newData?.pages[0] ?? [];
  }

  async function fetchNextTasks() {
    if (!result.hasNextPage) {
      return [];
    }

    const {data} = await result.fetchNextPage();
    function calculateNextTasksData(
      data?: InfiniteData<Task[]>,
    ): InfiniteData<Task[]> | undefined {
      const tasks = data?.pages.flat() ?? [];
      if (data === undefined || tasks.length <= MAX_TASKS_DISPLAYED) {
        return data;
      }
      const [firstTask, ...trimmedTasks] = tasks.slice(
        -(MAX_TASKS_DISPLAYED + 1),
      );

      return {
        pages: chunk(trimmedTasks, MAX_TASKS_PER_REQUEST),
        pageParams: [
          {
            searchAfter: firstTask.sortValues,
          },
          ...data.pageParams.slice(-(MAX_PAGE_COUNT - 1)),
        ],
      };
    }
    const newData = calculateNextTasksData(data);

    client.setQueryData<InfiniteData<Task[]>>(
      getQueryKey(Object.values(payload)),
      () => {
        return newData;
      },
    );

    return newData === undefined ? [] : newData.pages[newData.pages.length - 1];
  }

  return {...result, fetchPreviousTasks, fetchNextTasks};
}

export {useTasks};
