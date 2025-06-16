/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {observer} from 'mobx-react';
import {Job} from '@vzeta/camunda-api-zod-schemas/operate';
import {UseInfiniteQueryResult} from '@tanstack/react-query';
import {Layer} from '@carbon/react';

import {RequestError} from 'modules/request';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {formatDate} from 'modules/utils/date';

import {
  CellContainer,
  Content,
  StructuredList,
  WarningFilled,
  Dropdown,
  EmptyMessageWrapper,
  Stack,
} from './styled';

enum FilterLabelMapping {
  'All listeners' = 'ALL_LISTENERS',
  'Execution listeners' = 'EXECUTION_LISTENER',
  'User task listeners' = 'TASK_LISTENER',
}

type FilterLabelMappingType = typeof FilterLabelMapping;
type FilterLabelMappingKeys = keyof FilterLabelMappingType;

type SelectedItem = {
  selectedItem: FilterLabelMappingKeys;
};

type UseJobsResult = UseInfiniteQueryResult<Job[], RequestError>;
type Props = {
  jobs: Job[] | undefined;
  setListenerTypeFilter: React.Dispatch<
    React.SetStateAction<ListenerEntity['listenerType'] | undefined>
  >;
  fetchNextPage: UseJobsResult['fetchNextPage'];
  fetchPreviousPage: UseJobsResult['fetchPreviousPage'];
  hasNextPage: boolean;
  hasPreviousPage: boolean;
};

const Listeners: React.FC<Props> = observer(
  ({
    jobs,
    setListenerTypeFilter,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  }) => {
    const {metaData} = flowNodeMetaDataStore.state;
    const isUserTask = metaData?.instanceMetadata?.flowNodeType === 'USER_TASK';

    const [selectedOption, setSelectedOption] =
      useState<FilterLabelMappingKeys>('All listeners');

    const handleEmptyMessages = () => {
      if (isUserTask) {
        if (selectedOption === 'All listeners')
          return 'This flow node has no execution listeners nor user task listeners';

        if (selectedOption === 'User task listeners')
          return 'This flow node has no user task listeners';
      }

      return 'This flow node has no execution listeners';
    };

    const listeners = jobs ?? [];

    return (
      <Content>
        <Stack as={Layer} $isUserTask={isUserTask}>
          {isUserTask && (
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
  },
);

export {Listeners};
