/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {Layer} from '@carbon/react';

import {EmptyMessage} from 'modules/components/EmptyMessage';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {formatDate} from 'modules/utils/date';
import {isGlobalListener} from 'modules/utils/listeners';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useElementSelectionInstanceKey} from 'modules/hooks/useElementSelectionInstanceKey';
import {useJobs} from 'modules/queries/jobs/useJobs';

import {
  CellContainer,
  Content,
  StructuredList,
  WarningFilled,
  Dropdown,
  EmptyMessageWrapper,
  Stack,
  SourceTag,
} from './styled';

type ListenerTypeFilter = 'EXECUTION_LISTENER' | 'TASK_LISTENER';
type SourceFilter = 'ALL' | 'GLOBAL' | 'MODEL';

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

const ListenersTab: React.FC = () => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {resolvedElementInstance, selectedElementId} =
    useProcessInstanceElementSelection();
  const resolvedElementInstanceKey = useElementSelectionInstanceKey();

  const hasUserTaskSelected = resolvedElementInstance?.type === 'USER_TASK';

  const [listenerTypeFilter, setListenerTypeFilter] =
    useState<ListenerTypeFilter>();
  const [selectedOption, setSelectedOption] =
    useState<FilterLabelMappingKeys>('All listeners');
  const [sourceFilter, setSourceFilter] = useState<SourceFilter>('ALL');

  const {
    data: jobs,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
    isFetching,
  } = useJobs({
    payload: {
      filter: {
        processInstanceKey: processInstanceId,
        elementId: selectedElementId ?? undefined,
        elementInstanceKey: resolvedElementInstanceKey ?? undefined,
        kind: listenerTypeFilter,
      },
    },
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const listeners = jobs ?? [];

  const filterBySource = (items: typeof listeners) => {
    if (sourceFilter === 'ALL') return items;
    return items.filter((item) => {
      const isGlobal = isGlobalListener(item.tags ?? []);
      return sourceFilter === 'GLOBAL' ? isGlobal : !isGlobal;
    });
  };

  const filteredListeners = filterBySource(listeners);

  // Auto-fetch next pages when source filter is active but no matches on current pages
  useEffect(() => {
    if (
      sourceFilter !== 'ALL' &&
      filteredListeners.length === 0 &&
      listeners.length > 0 &&
      hasNextPage &&
      !isFetching
    ) {
      fetchNextPage();
    }
  }, [
    sourceFilter,
    filteredListeners.length,
    listeners.length,
    hasNextPage,
    isFetching,
    fetchNextPage,
  ]);

  const handleEmptyMessages = () => {
    if (sourceFilter === 'GLOBAL') {
      return 'No global listeners match the selected filter';
    }
    if (sourceFilter === 'MODEL') {
      return 'No model listeners match the selected filter';
    }

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
            selectedOption === 'All listeners' && listeners?.length === 0
          }
        />
      )}
      <Dropdown
        id="sourceFilter"
        data-testid="source-filter"
        titleText="Source"
        label="All sources"
        hideLabel
        onChange={({selectedItem}: {selectedItem: string}) => {
          const value =
            selectedItem === 'Global'
              ? 'GLOBAL'
              : selectedItem === 'Model'
                ? 'MODEL'
                : 'ALL';
          setSourceFilter(value);
        }}
        items={['All sources', 'Global', 'Model']}
        size="sm"
        selectedItem={
          sourceFilter === 'GLOBAL'
            ? 'Global'
            : sourceFilter === 'MODEL'
              ? 'Model'
              : 'All sources'
        }
        disabled={listeners?.length === 0}
      />
      <Stack as={Layer}>
        {listeners.length > 0 ? (
          filteredListeners.length > 0 ? (
            <StructuredList
              dataTestId="listeners-list"
              headerColumns={[
                {cellContent: 'Listener type', width: '17%'},
                {cellContent: 'Source', width: '10%'},
                {cellContent: 'Listener key', width: '17%'},
                {cellContent: 'State', width: '10%'},
                {cellContent: 'Job type', width: '13%'},
                {cellContent: 'Event', width: '13%'},
                {cellContent: 'Time', width: '20%'},
              ]}
              headerSize="sm"
              verticalCellPadding="var(--cds-spacing-02)"
              label="Listeners List"
              onVerticalScrollStartReach={() => {
                if (hasPreviousPage) {
                  fetchPreviousPage();
                }
              }}
              onVerticalScrollEndReach={() => {
                if (hasNextPage) {
                  fetchNextPage();
                }
              }}
              rows={filteredListeners?.map(
                ({
                  kind,
                  jobKey,
                  state,
                  type,
                  listenerEventType,
                  endTime,
                  tags,
                }) => {
                  return {
                    key: jobKey,
                    dataTestId: jobKey,
                    columns: [
                      {
                        cellContent: (
                          <CellContainer orientation="horizontal" gap={3}>
                            {spaceAndCapitalize(kind)}
                            {state === 'FAILED' && <WarningFilled />}
                          </CellContainer>
                        ),
                      },
                      {
                        cellContent: (
                          <CellContainer>
                            <SourceTag
                              type={
                                isGlobalListener(tags ?? [])
                                  ? 'blue'
                                  : 'high-contrast'
                              }
                              size="sm"
                            >
                              {isGlobalListener(tags ?? [])
                                ? 'Global'
                                : 'Model'}
                            </SourceTag>
                          </CellContainer>
                        ),
                      },
                      {cellContent: <CellContainer>{jobKey}</CellContainer>},
                      {
                        cellContent: (
                          <CellContainer>
                            {spaceAndCapitalize(state)}
                          </CellContainer>
                        ),
                      },
                      {
                        cellContent: <CellContainer>{type}</CellContainer>,
                      },
                      {
                        cellContent: (
                          <CellContainer>
                            {spaceAndCapitalize(listenerEventType)}
                          </CellContainer>
                        ),
                      },
                      {
                        cellContent: (
                          <CellContainer>{formatDate(endTime)}</CellContainer>
                        ),
                      },
                    ],
                  };
                },
              )}
            />
          ) : (
            !hasNextPage && (
              <EmptyMessageWrapper>
                <EmptyMessage message={handleEmptyMessages()} />
              </EmptyMessageWrapper>
            )
          )
        ) : (
          <EmptyMessageWrapper>
            <EmptyMessage message={handleEmptyMessages()} />
          </EmptyMessageWrapper>
        )}
      </Stack>
    </Content>
  );
};

export {ListenersTab};
