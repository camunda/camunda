/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState, useRef} from 'react';
import Table from 'modules/components/Table';
import {InstancesMessage} from 'modules/components/InstancesMessage';
import {EXPAND_STATE} from 'modules/constants';
import {Skeleton} from './Skeleton';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {instancesStore, MAX_INSTANCES_STORED} from 'modules/stores/instances';
import usePrevious from 'modules/hooks/usePrevious';
import {autorun} from 'mobx';
import {Header} from './Header';
import {Instances} from './Instances';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {useFilters} from 'modules/hooks/useFilters';

const ROW_HEIGHT = 38;

type ListProps = {
  Overlay?: any;
  onSort?: () => void;
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const List: React.FC<ListProps> = observer((props) => {
  let containerRef: any = React.createRef();
  let listRef: any = useRef();
  const prevExpandState = usePrevious(props.expandState);
  const [entriesPerPage, setEntriesPerPage] = useState(0);
  const filters = useFilters();

  const {
    areProcessInstancesEmpty,
    state: {status},
  } = instancesStore;

  useEffect(() => {
    if (
      prevExpandState !== props.expandState &&
      props.expandState !== EXPAND_STATE.COLLAPSED
    ) {
      if (containerRef.current?.clientHeight > 0) {
        const rows = ~~(containerRef.current.clientHeight / ROW_HEIGHT) - 1;
        setEntriesPerPage(rows);
      }
    }
  }, [props.expandState, prevExpandState, containerRef]);

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
            <Skeleton {...props} rowsToDisplay={entriesPerPage} />
          )}
          {status === 'error' && <InstancesMessage type="error" />}
          {status === 'fetched' && areProcessInstancesEmpty && (
            <InstancesMessage
              type="empty"
              areInstanceStateFiltersApplied={filters.areProcessInstanceStatesApplied()}
            />
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
