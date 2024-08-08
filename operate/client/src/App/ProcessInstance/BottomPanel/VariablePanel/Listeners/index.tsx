/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {CellContainer, Content, StructuredList} from './styled';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';

type Props = {
  listeners: ListenerEntity[];
};

const Listeners: React.FC<Props> = observer(({listeners}) => {
  const ROW_HEIGHT = 45;

  return (
    <Content>
      <StructuredList
        dataTestId="listeners-list"
        headerColumns={[
          {cellContent: 'Listener type', width: '15%'},
          {cellContent: 'Listener key', width: '20%'},
          {cellContent: 'State', width: '15%'},
          {cellContent: 'Job type', width: '15%'},
          {cellContent: 'Event', width: '15%'},
          {cellContent: 'Time', width: '20%'},
        ]}
        headerSize="sm"
        verticalCellPadding="var(--cds-spacing-02)"
        label="Listeners List"
        onVerticalScrollStartReach={() => {
          console.log('listener start');
        }} // @TODO: INFINITE SCROLL START
        onVerticalScrollEndReach={() => {
          if (
            processInstanceListenersStore.shouldFetchNextListeners() === false
          ) {
            return;
          }

          processInstanceListenersStore.fetchNextInstances();
        }} // @TODO: INFINITE SCROLL END
        rows={listeners.map(
          ({listenerType, listenerKey, state, jobType, event, time}) => {
            return {
              key: `${listenerKey}`,
              dataTestId: `${listenerKey}`,
              columns: [
                {
                  cellContent: (
                    <CellContainer>
                      {spaceAndCapitalize(listenerType)}
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
                  cellContent: (
                    <CellContainer>{spaceAndCapitalize(jobType)}</CellContainer>
                  ),
                },
                {
                  cellContent: (
                    <CellContainer>{spaceAndCapitalize(event)}</CellContainer>
                  ),
                },
                {cellContent: <CellContainer>{time}</CellContainer>},
              ],
            };
          },
        )}
      />
    </Content>
  );
});

export {Listeners};
