/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Layer} from '@carbon/react';

import {EmptyMessage} from 'modules/components/EmptyMessage';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {formatDate} from 'modules/utils/date';
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
} from './styled';

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

  const {
    data: jobs,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  } = useJobs({
    payload: {
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

  const listeners = jobs ?? [];

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
      <Stack as={Layer}>
        {listeners?.length > 0 ? (
          <StructuredList
            dataTestId="listeners-list"
            headerColumns={[
              {cellContent: 'Listener type', width: '20%'},
              {cellContent: 'Listener key', width: '20%'},
              {cellContent: 'State', width: '10%'},
              {cellContent: 'Job type', width: '15%'},
              {cellContent: 'Event', width: '15%'},
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
            rows={listeners?.map(
              ({kind, jobKey, state, type, listenerEventType, endTime}) => {
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
          <EmptyMessageWrapper>
            <EmptyMessage message={handleEmptyMessages()} />
          </EmptyMessageWrapper>
        )}
      </Stack>
    </Content>
  );
};

export {ListenersTab};
