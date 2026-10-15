/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {z} from 'zod';

import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useElementSelectionInstanceKey} from 'modules/hooks/useElementSelectionInstanceKey';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';

import {CellContainer, Content, WarningFilled, Dropdown} from './styled';

type ListenerTypeFilter = 'EXECUTION_LISTENER' | 'TASK_LISTENER';

const FilterLabelMapping = {
  'All listeners': 'ALL_LISTENERS',
  'Execution listeners': 'EXECUTION_LISTENER',
  'User task listeners': 'TASK_LISTENER',
} as const satisfies Record<string, ListenerTypeFilter | 'ALL_LISTENERS'>;

type FilterLabelMappingType = typeof FilterLabelMapping;
type FilterLabelMappingKeys = keyof FilterLabelMappingType;

type SelectedItem = {
  selectedItem: FilterLabelMappingKeys;
};

/**
 * `jobKey` is engine-assigned and monotonically increasing, so sorting by it
 * descending reliably yields the most recent listener executions first. This is
 * more robust than `endTime`, which is null for listeners that have not
 * completed yet.
 */
const DEFAULT_SORT_FIELD = 'jobKey';

const jobSortFieldEnum = z.enum([
  'jobKey',
  'type',
  'state',
  'kind',
  'listenerEventType',
  'endTime',
]);

const headerColumns = [
  {header: 'Listener type', key: 'kind', sortKey: 'kind'},
  {
    header: 'Listener key',
    key: 'jobKey',
    sortKey: 'jobKey',
    isDefault: true,
  },
  {header: 'State', key: 'state', sortKey: 'state'},
  {header: 'Job type', key: 'type', sortKey: 'type'},
  {header: 'Event', key: 'listenerEventType', sortKey: 'listenerEventType'},
  {header: 'Time', key: 'endTime', sortKey: 'endTime'},
];

const ListenersTab: React.FC = () => {
  const location = useLocation();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {resolvedElementInstance, selectedElementId} =
    useProcessInstanceElementSelection();
  const resolvedElementInstanceKey = useElementSelectionInstanceKey();

  const hasUserTaskSelected = resolvedElementInstance?.type === 'USER_TASK';

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerTypeFilter>();
  const [selectedOption, setSelectedOption] =
    useState<FilterLabelMappingKeys>('All listeners');

  const sortParams = getSortParams(location.search) ?? {
    sortBy: DEFAULT_SORT_FIELD,
    sortOrder: 'desc' as const,
  };
  const sortByParsed = jobSortFieldEnum.safeParse(sortParams.sortBy);
  const sortBy = sortByParsed.success ? sortByParsed.data : DEFAULT_SORT_FIELD;

  const {
    data: jobs,
    isPending,
    isLoading,
    error,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
    isFetchingNextPage,
    isFetchingPreviousPage,
  } = useJobs({
    payload: {
      sort: [{field: sortBy, order: sortParams.sortOrder}],
      filter: {
        processInstanceKey: processInstanceId,
        elementId: selectedElementId ?? undefined,
        elementInstanceKey: resolvedElementInstanceKey ?? undefined,
        kind: listenerTypeFilter ?? {
          $in: ['EXECUTION_LISTENER', 'TASK_LISTENER'],
        },
      },
    },
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const listeners = useMemo(() => jobs ?? [], [jobs]);

  const rows = useMemo(
    () =>
      listeners.map(
        ({kind, jobKey, state, type, listenerEventType, endTime}) => ({
          id: jobKey,
          kind: (
            <CellContainer orientation="horizontal" gap={3}>
              {spaceAndCapitalize(kind)}
              {state === 'FAILED' && <WarningFilled />}
            </CellContainer>
          ),
          jobKey,
          state: spaceAndCapitalize(state),
          type,
          listenerEventType: spaceAndCapitalize(listenerEventType),
          endTime: formatDate(endTime),
        }),
      ),
    [listeners],
  );

  const handleEmptyMessages = () => {
    if (hasUserTaskSelected) {
      if (selectedOption === 'All listeners') {
        return 'This element has no execution listeners nor user task listeners';
      }

      if (selectedOption === 'User task listeners') {
        return 'This element has no user task listeners';
      }
    }

    return 'This element has no execution listeners';
  };

  const getTableState = () => {
    if (isPending) {
      return 'skeleton';
    }

    if (isLoading) {
      return 'loading';
    }

    if (error) {
      return 'error';
    }

    if (rows.length === 0) {
      return 'empty';
    }

    return 'content';
  };

  return (
    <Content>
      {hasUserTaskSelected && (
        <Dropdown
          id="listenerTypeFilter"
          data-testid="listener-type-filter"
          titleText="Listener type"
          label="All listeners"
          hideLabel
          onChange={async ({selectedItem}: SelectedItem) => {
            if (selectedItem !== selectedOption) {
              setSelectedOption(selectedItem);

              if (FilterLabelMapping[selectedItem] !== 'ALL_LISTENERS') {
                setListenerTypeFilter(FilterLabelMapping[selectedItem]);
              } else {
                setListenerTypeFilter(undefined);
              }
            }
          }}
          items={Object.keys(FilterLabelMapping)}
          size="sm"
          selectedItem={selectedOption}
          disabled={
            selectedOption === 'All listeners' && listeners.length === 0
          }
        />
      )}
      <PaginatedSortableTable
        state={getTableState()}
        rows={rows}
        emptyMessage={{message: handleEmptyMessages()}}
        headerColumns={headerColumns}
        pagination={{
          hasPreviousPage,
          hasNextPage,
          isFetchingPreviousPage,
          isFetchingNextPage,
          fetchPreviousPage,
          fetchNextPage,
        }}
        stickyHeader
      />
    </Content>
  );
};

export {ListenersTab};
