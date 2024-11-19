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
} from './styled';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {
  MAX_LISTENERS_STORED,
  processInstanceListenersStore,
} from 'modules/stores/processInstanceListeners';
import {formatDate} from 'modules/utils/date';
import {useState} from 'react';

type Props = {
  listeners: ListenerEntity[];
};

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

const Listeners: React.FC<Props> = observer(({listeners}) => {
  const {setListenerTypeFilter} = processInstanceListenersStore;

  const [selectedOption, setSelectedOption] =
    useState<FilterLabelMappingKeys>('All listeners');

  return (
    <Content>
      <Dropdown
        id="listenerTypeFilter"
        data-testid="listener-type-filter"
        titleText="Listener type"
        label="All listeners"
        hideLabel
        onChange={async ({selectedItem}: SelectedItem) => {
          setSelectedOption(selectedItem);

          if (FilterLabelMapping[selectedItem] !== 'ALL_LISTENERS') {
            setListenerTypeFilter(FilterLabelMapping[selectedItem]);
          } else {
            setListenerTypeFilter(undefined);
          }
        }}
        items={Object.keys(FilterLabelMapping)}
        size="sm"
        selectedItem={selectedOption}
      />
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
          if (
            processInstanceListenersStore.shouldFetchPreviousListeners() ===
            false
          ) {
            return;
          }

          await processInstanceListenersStore.fetchPreviousListeners();

          if (
            processInstanceListenersStore.state?.listeners?.length ===
              MAX_LISTENERS_STORED &&
            processInstanceListenersStore.state.latestFetch?.itemsCount !== 0 &&
            processInstanceListenersStore.state.latestFetch !== null
          ) {
            scrollDown(
              processInstanceListenersStore.state.latestFetch.itemsCount *
                ROW_HEIGHT,
            );
          }
        }}
        onVerticalScrollEndReach={() => {
          if (
            processInstanceListenersStore.shouldFetchNextListeners() === false
          ) {
            return;
          }

          processInstanceListenersStore.fetchNextListeners();
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
                    <CellContainer>{spaceAndCapitalize(state)}</CellContainer>
                  ),
                },
                {
                  cellContent: <CellContainer>{jobType}</CellContainer>,
                },
                {
                  cellContent: (
                    <CellContainer>{spaceAndCapitalize(event)}</CellContainer>
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
    </Content>
  );
});

export {Listeners};
