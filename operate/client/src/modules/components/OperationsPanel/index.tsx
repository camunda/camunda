/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import React, {useEffect, useRef} from 'react';

import {CollapsablePanel as BaseCollapsablePanel} from 'modules/components/CollapsablePanel';
import {observer} from 'mobx-react';
import {panelStatesStore} from 'modules/stores/panelStates';
import {operationsStore} from 'modules/stores/operations';
import {OperationsList, EmptyMessageContainer} from './styled';
import OperationsEntry from './OperationsEntry';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {EMPTY_MESSAGE} from './constants';
import {InlineNotification} from '@carbon/react';
import {Skeleton} from './Skeleton';

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

  const shouldDisplaySkeleton = ['initial', 'first-fetch'].includes(status);

  return (
    <BaseCollapsablePanel
      label="Operations"
      panelPosition="RIGHT"
      maxWidth={478}
      isOverlay
      isCollapsed={isOperationsCollapsed}
      onToggle={toggleOperationsPanel}
      ref={scrollableContainerRef}
      scrollable={!shouldDisplaySkeleton}
      collapsablePanelRef={collapsablePanelRef}
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

        if (shouldDisplaySkeleton) {
          return <Skeleton />;
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
