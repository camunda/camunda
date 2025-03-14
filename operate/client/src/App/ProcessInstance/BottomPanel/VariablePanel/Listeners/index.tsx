/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {
  CellContainer,
  Content,
  StructuredList,
  WarningFilled,
  Dropdown,
  EmptyMessageWrapper,
  Stack,
} from './styled';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {
  MAX_LISTENERS_STORED,
  processInstanceListenersStore,
} from 'modules/stores/processInstanceListeners';
import {formatDate} from 'modules/utils/date';
import {useState} from 'react';
import {Layer} from '@carbon/react';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';

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

const ROW_HEIGHT = 46;

const Listeners: React.FC = observer(() => {
  const {
    setListenerTypeFilter,
    shouldFetchPreviousListeners,
    fetchPreviousListeners,
    shouldFetchNextListeners,
    fetchNextListeners,
  } = processInstanceListenersStore;
  const {listeners, latestFetch} = processInstanceListenersStore.state;

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
            onVerticalScrollStartReach={async (scrollDown) => {
              if (shouldFetchPreviousListeners() === false) {
                return;
              }

              await fetchPreviousListeners();

              if (
                listeners?.length === MAX_LISTENERS_STORED &&
                latestFetch?.itemsCount !== 0 &&
                latestFetch !== null
              ) {
                scrollDown(latestFetch.itemsCount * ROW_HEIGHT);
              }
            }}
            onVerticalScrollEndReach={() => {
              if (shouldFetchNextListeners() === false) {
                return;
              }

              fetchNextListeners();
            }}
            rows={listeners?.map(
              ({listenerType, listenerKey, state, jobType, event, time}) => {
                return {
                  key: listenerKey,
                  dataTestId: listenerKey,
                  columns: [
                    {
                      cellContent: (
                        <CellContainer orientation="horizontal" gap={3}>
                          {spaceAndCapitalize(listenerType)}
                          {state === 'FAILED' && <WarningFilled />}
                        </CellContainer>
                      ),
                    },
                    {cellContent: <CellContainer>{listenerKey}</CellContainer>},
                    {
                      cellContent: (
                        <CellContainer>
                          {spaceAndCapitalize(state)}
                        </CellContainer>
                      ),
                    },
                    {
                      cellContent: <CellContainer>{jobType}</CellContainer>,
                    },
                    {
                      cellContent: (
                        <CellContainer>
                          {spaceAndCapitalize(event)}
                        </CellContainer>
                      ),
                    },
                    {
                      cellContent: (
                        <CellContainer>{formatDate(time)}</CellContainer>
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
});

export {Listeners};
