/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from 'modules/components/CollapsablePanel';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import {operationsStore} from 'modules/stores/operations';
import * as Styled from './styled';
import * as CONSTANTS from './constants';
import OperationsEntry from './OperationsEntry';
import Skeleton from './Skeleton';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';

type Props = {
  isOperationsCollapsed: boolean;
  toggleOperations: () => void;
};

const OperationsPanel: React.FC<Props> = observer(
  ({isOperationsCollapsed, toggleOperations}) => {
    const {operations, status, hasMoreOperations} = operationsStore.state;

    useEffect(() => {
      operationsStore.init();
      return operationsStore.reset;
    }, []);

    return (
      <InfiniteScroller
        onVerticalScrollEndReach={() => {
          if (hasMoreOperations && status !== 'fetching') {
            operationsStore.fetchNextOperations(
              operations[operations.length - 1].sortValues
            );
          }
        }}
      >
        <CollapsablePanel
          label={CONSTANTS.OPERATIONS_LABEL}
          panelPosition={PANEL_POSITION.RIGHT}
          maxWidth={478}
          isOverlay
          isCollapsed={isOperationsCollapsed}
          toggle={toggleOperations}
          hasBackgroundColor
          verticalLabelOffset={27}
          scrollable
        >
          <Styled.OperationsList
            data-testid="operations-list"
            isInitialLoadComplete={status === 'fetched'}
          >
            {['initial', 'fetching'].includes(status) && <Skeleton />}
            {operations.map((operation) => (
              <OperationsEntry key={operation.id} operation={operation} />
            ))}
            {operations.length === 0 && status === 'fetched' && (
              <Styled.EmptyMessage>
                {CONSTANTS.EMPTY_MESSAGE}
              </Styled.EmptyMessage>
            )}
            {status === 'error' && (
              <Styled.EmptyMessage>
                <StatusMessage variant="error">
                  Operations could not be fetched
                </StatusMessage>
              </Styled.EmptyMessage>
            )}
          </Styled.OperationsList>
        </CollapsablePanel>
      </InfiniteScroller>
    );
  }
);

export default withCollapsablePanel(OperationsPanel);
