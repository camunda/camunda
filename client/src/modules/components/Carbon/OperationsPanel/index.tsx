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
import {OperationsList} from './styled';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import OperationsEntry from './OperationsEntry';

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
    </BaseCollapsablePanel>
  );
});

export {OperationsPanel};
