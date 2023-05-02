/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';

import {CollapsablePanel as BaseCollapsablePanel} from 'modules/components/Carbon/CollapsablePanel';
import {observer} from 'mobx-react';
import {panelStatesStore} from 'modules/stores/panelStates';
import {operationsStore} from 'modules/stores/operations';
import {OperationsList, EmptyMessageContainer} from './styled';
import OperationsEntry from './OperationsEntry';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {EMPTY_MESSAGE} from './constants';
import {InlineNotification} from '@carbon/react';

const OperationsPanel: React.FC = observer(() => {
  const {operations, status, hasMoreOperations} = operationsStore.state;

  const {
    state: {isOperationsCollapsed},
    toggleOperationsPanel,
  } = panelStatesStore;

  useEffect(() => {
    operationsStore.init();
    return operationsStore.reset;
  }, []);

  const scrollableContainerRef = useRef<HTMLDivElement | null>(null);
  const collapsablePanelRef = useRef<HTMLDivElement | null>(null);

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
    >
      {(() => {
        if (operations.length === 0 && status === 'fetched') {
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

        if (status === 'error') {
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

        return (
          <InfiniteScroller
            onVerticalScrollEndReach={() => {
              if (hasMoreOperations && status !== 'fetching') {
                operationsStore.fetchNextOperations();
              }
            }}
            scrollableContainerRef={scrollableContainerRef}
          >
            <OperationsList data-testid="operations-list">
              {operations.map((operation) => (
                <OperationsEntry key={operation.id} operation={operation} />
              ))}
            </OperationsList>
          </InfiniteScroller>
        );
      })()}
    </BaseCollapsablePanel>
  );
});

export {OperationsPanel};
