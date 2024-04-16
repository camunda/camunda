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

import {TaskFilters} from 'modules/hooks/useTaskFilters';
import {TasksSearchBody} from 'modules/types';
import {getStateLocally} from './localStorage';
import {formatRFC3339} from 'date-fns';

const SORT_BY_FIELD: Record<
  TaskFilters['sortBy'],
  'creationTime' | 'dueDate' | 'followUpDate' | 'completionTime'
> = {
  creation: 'creationTime',
  due: 'dueDate',
  'follow-up': 'followUpDate',
  completion: 'completionTime',
};

const getQueryVariables = (
  filters: TaskFilters,
  {
    assignee,
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  }: Pick<
    TasksSearchBody,
    | 'assignee'
    | 'pageSize'
    | 'searchBefore'
    | 'searchAfter'
    | 'searchAfterOrEqual'
  >,
): TasksSearchBody => {
  const {filter, sortBy, sortOrder, ...remainingFilters} = filters;
  const BASE_QUERY_VARIABLES: TasksSearchBody = {
    sort: [
      {
        field: SORT_BY_FIELD[sortBy],
        order: sortOrder.toUpperCase() as 'ASC' | 'DESC',
      },
    ],
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  };
  const {taskVariables, ...parsedFilters} =
    convertFiltersToQueryVariables(remainingFilters);

  switch (filter) {
    case 'assigned-to-me': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: true,
        assignee: assignee!,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
    case 'unassigned': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: false,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
    case 'completed': {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'COMPLETED',
        ...parsedFilters,
      };
    }
    case 'custom': {
      return {
        ...BASE_QUERY_VARIABLES,
        ...parsedFilters,
        taskVariables,
      };
    }
    case 'all-open':
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
  }
};

function convertFiltersToQueryVariables(
  filters: Omit<TaskFilters, 'filter' | 'sortBy' | 'sortOrder'>,
): TasksSearchBody {
  const {
    dueDateFrom,
    dueDateTo,
    followUpDateFrom,
    followUpDateTo,
    ...restFilters
  } = filters;
  const updatedFilters: TasksSearchBody = restFilters;
  const customFilters = getStateLocally('customFilters')?.custom;

  if (customFilters !== undefined && Array.isArray(customFilters?.variables)) {
    updatedFilters.taskVariables = customFilters.variables.map<{
      name: string;
      value: string;
      operator: 'eq';
    }>(({name, value}) => ({
      name: name!,
      value: value!,
      operator: 'eq',
    }));
  }

  if (filters.dueDateFrom !== undefined) {
    updatedFilters.dueDate = {
      from: formatRFC3339(filters.dueDateFrom),
    };
  }

  if (filters.dueDateTo !== undefined) {
    updatedFilters.dueDate = {
      ...updatedFilters.dueDate,
      to: formatRFC3339(filters.dueDateTo),
    };
  }

  if (filters.followUpDateFrom !== undefined) {
    updatedFilters.followUpDate = {
      from: formatRFC3339(filters.followUpDateFrom),
    };
  }

  if (filters.followUpDateTo !== undefined) {
    updatedFilters.followUpDate = {
      ...updatedFilters.followUpDate,
      to: formatRFC3339(filters.followUpDateTo),
    };
  }

  return updatedFilters;
}

export {getQueryVariables};
