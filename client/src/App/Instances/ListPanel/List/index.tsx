/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';
import Table from 'modules/components/Table';
import {Message} from 'modules/components/SortableTable/Message';
import {Skeleton} from './Skeleton';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {instancesStore, MAX_INSTANCES_STORED} from 'modules/stores/instances';
import {autorun} from 'mobx';
import {Header} from './Header';
import {Instances} from './Instances';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {useFilters} from 'modules/hooks/useFilters';

const ROW_HEIGHT = 38;

type ListProps = {
  Overlay?: any;
  onSort?: () => void;
};

const List: React.FC<ListProps> = observer((props) => {
  let containerRef: any = React.createRef();
  let listRef: any = useRef();
  const filters = useFilters();

  const {
    areProcessInstancesEmpty,
    state: {status},
  } = instancesStore;

  const getEmptyListMessage = () => {
    return `There are no Instances matching this filter set${
      filters.areProcessInstanceStatesApplied()
        ? ''
        : '\n To see some results, select at least one Instance state'
    }`;
  };

  useEffect(() => {
    let disposer = autorun(() => {
      if (instancesStore.state.status === 'fetching') {
        listRef.current.scrollTo?.(0, 0);
      }
    });

    return () => {
      if (disposer !== undefined) {
        disposer();
      }
    };
  }, []);

  return (
    <Styled.List data-testid="process-instances-list" ref={listRef}>
      <Styled.TableContainer ref={containerRef}>
        {(status === 'fetching' || status === 'refetching') && (
          <Styled.Spinner data-testid="instances-loader" />
        )}

        <Table>
          <Header />
          {['initial', 'first-fetch'].includes(status) && (
            <Skeleton {...props} rowsToDisplay={50} />
          )}
          {status === 'error' && <Message type="error" />}
          {status === 'fetched' && areProcessInstancesEmpty && (
            <Message type="empty">{getEmptyListMessage()}</Message>
          )}
          {status === 'refetching' ? null : (
            <InfiniteScroller
              onVerticalScrollStartReach={async (scrollDown) => {
                if (instancesStore.shouldFetchPreviousInstances() === false) {
                  return;
                }

                await instancesStore.fetchPreviousInstances();

                if (
                  instancesStore.state.processInstances.length ===
                    MAX_INSTANCES_STORED &&
                  instancesStore.state.latestFetch?.processInstancesCount !==
                    0 &&
                  instancesStore.state.latestFetch !== null
                ) {
                  scrollDown(
                    instancesStore.state.latestFetch.processInstancesCount *
                      ROW_HEIGHT
                  );
                }
              }}
              onVerticalScrollEndReach={() => {
                if (instancesStore.shouldFetchNextInstances() === false) {
                  return;
                }

                instancesStore.fetchNextInstances();
              }}
              scrollableContainerRef={listRef}
            >
              <Instances />
            </InfiniteScroller>
          )}
        </Table>
      </Styled.TableContainer>
    </Styled.List>
  );
});

export default List;
