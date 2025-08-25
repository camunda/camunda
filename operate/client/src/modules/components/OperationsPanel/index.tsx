/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useRef} from 'react';

import {CollapsablePanel as BaseCollapsablePanel} from 'modules/components/CollapsablePanel';
import {observer} from 'mobx-react';
import {panelStatesStore} from 'modules/stores/panelStates';
import {OperationsList, EmptyMessageContainer} from './styled';
import {OperationsEntry} from './OperationsEntry';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {EMPTY_MESSAGE} from './constants';
import {InlineNotification} from '@carbon/react';
import {Skeleton} from './Skeleton';
import {useBatchOperations} from 'modules/queries/batch-operations/useBatchOperations';

const OperationsPanel: React.FC = observer(() => {
  const {
    state: {isOperationsCollapsed},
    toggleOperationsPanel,
  } = panelStatesStore;

  const scrollableContainerRef = useRef<HTMLDivElement | null>(null);
  const collapsablePanelRef = useRef<HTMLDivElement | null>(null);

  const {data, isError, isLoading, isFetched, isFetching, fetchNextPage} =
    useBatchOperations({
      sort: [{field: 'startDate', order: 'desc'}],
    });

  const operations = data ?? [];

  useEffect(() => {
    if (collapsablePanelRef.current !== null) {
      panelStatesStore.setOperationsPanelRef(collapsablePanelRef);
    }
  }, []);

  return (
    <BaseCollapsablePanel
      label="Operations"
      panelPosition="RIGHT"
      maxWidth={478}
      isOverlay
      isCollapsed={isOperationsCollapsed}
      onToggle={toggleOperationsPanel}
      ref={scrollableContainerRef}
      scrollable={!isLoading}
      collapsablePanelRef={collapsablePanelRef}
    >
      {(() => {
        if (isError) {
          return (
            <EmptyMessageContainer>
              <InlineNotification
                kind="error"
                lowContrast
                title=""
                subtitle="Operations could not be fetched"
                hideCloseButton
              />
            </EmptyMessageContainer>
          );
        }

        if (operations.length === 0 && isFetched) {
          return (
            <EmptyMessageContainer>
              <InlineNotification
                kind="info"
                lowContrast
                title=""
                subtitle={EMPTY_MESSAGE}
                hideCloseButton
              />
            </EmptyMessageContainer>
          );
        }

        if (isLoading) {
          return <Skeleton />;
        }

        return (
          <InfiniteScroller
            onVerticalScrollEndReach={() => {
              if (!isFetching) {
                fetchNextPage();
              }
            }}
            scrollableContainerRef={scrollableContainerRef}
          >
            <OperationsList data-testid="operations-list">
              {operations.map((operation) => (
                <OperationsEntry
                  key={operation.batchOperationKey}
                  operation={operation}
                />
              ))}
            </OperationsList>
          </InfiniteScroller>
        );
      })()}
    </BaseCollapsablePanel>
  );
});

export {OperationsPanel};
