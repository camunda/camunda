/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef} from 'react';
import {CollapsablePanel} from 'modules/components/CollapsablePanel';
import {observer} from 'mobx-react';
import {panelStatesStore} from 'modules/stores/panelStates';
import {OperationsList, EmptyMessageContainer, ScrollContainer} from './styled';
import {OperationsEntry} from './OperationsEntry';

import {EMPTY_MESSAGE, OPERATIONS_EXPANDED_PANEL_WIDTH} from './constants';
import {InlineNotification} from '@carbon/react';
import {ListSkeleton} from './Skeleton/ListSkeleton';
import {OperationEntry} from './Skeleton/OperationEntry';
import {useBatchOperations} from 'modules/queries/batch-operations/useBatchOperations';
import {useVirtualizer} from '@tanstack/react-virtual';

function getPageParam(param: unknown) {
  if (typeof param === 'number') {
    return param;
  }
  return 0;
}

const OperationsPanel: React.FC = observer(() => {
  const {
    state: {isOperationsCollapsed},
    toggleOperationsPanel,
  } = panelStatesStore;

  const scrollableContainerRef = useRef<HTMLDivElement | null>(null);

  const {
    data,
    isError,
    isLoading,
    isFetched,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    fetchPreviousPage,
    hasPreviousPage,
    isFetchingPreviousPage,
  } = useBatchOperations({
    sort: [{field: 'endDate', order: 'desc'}],
  });
  const firstPageParam = getPageParam(data?.pageParams[0]);
  const operations = data?.pages?.flatMap((page) => page.items) ?? [];

  const virtualizer = useVirtualizer({
    count:
      Array.isArray(data?.pages) && data.pages.length > 0
        ? data.pages[0].page.totalItems
        : 0,
    getScrollElement: () => scrollableContainerRef.current,
    estimateSize: () => 122,
    overscan: 5,
    onChange: ({scrollDirection, getVirtualItems}) => {
      const virtualItems = getVirtualItems();
      const firstItem = virtualItems.at(0);
      const lastItem = virtualItems.at(-1);

      if (
        scrollDirection === 'backward' &&
        firstItem !== undefined &&
        firstItem.index <= firstPageParam &&
        hasPreviousPage &&
        !isFetchingPreviousPage
      ) {
        fetchPreviousPage();
      }

      if (
        scrollDirection === 'forward' &&
        lastItem !== undefined &&
        lastItem.index - firstPageParam >= operations.length - 1 &&
        hasNextPage &&
        !isFetchingNextPage
      ) {
        fetchNextPage();
      }
    },
  });
  const virtualItems = virtualizer.getVirtualItems();

  return (
    <CollapsablePanel
      label="Operations"
      panelPosition="RIGHT"
      maxWidth={OPERATIONS_EXPANDED_PANEL_WIDTH}
      isOverlay
      isCollapsed={isOperationsCollapsed}
      onToggle={toggleOperationsPanel}
      ref={scrollableContainerRef}
      scrollable={!isLoading}
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
          return <ListSkeleton />;
        }

        return (
          <OperationsList
            data-testid="operations-list"
            $height={virtualizer.getTotalSize()}
          >
            {virtualItems.map((virtualItem) => {
              const realIndex = virtualItem.index - firstPageParam;
              const isLoaderRow = realIndex > operations.length - 1;
              const operation = operations[Math.max(0, realIndex)];

              return (
                <ScrollContainer
                  key={virtualItem.key}
                  $height={virtualItem.size}
                  $transform={virtualItem.start}
                >
                  {isLoaderRow ? (
                    <OperationEntry />
                  ) : (
                    <OperationsEntry operation={operation} />
                  )}
                </ScrollContainer>
              );
            })}
          </OperationsList>
        );
      })()}
    </CollapsablePanel>
  );
});

export {OperationsPanel};
