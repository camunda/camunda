/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState, useRef} from 'react';
import Table from 'modules/components/Table';
import {Message} from './Message';
import {EXPAND_STATE} from 'modules/constants';
import {Skeleton} from './Skeleton';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {instancesStore, MAX_INSTANCES_STORED} from 'modules/stores/instances';
import usePrevious from 'modules/hooks/usePrevious';
import {autorun} from 'mobx';
import {Header} from './Header';
import {Instances} from './Instances';

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
    <Styled.List
      id="scrollable-list"
      ref={listRef}
      onScroll={async (event) => {
        const target = event.target as HTMLDivElement;
        const shouldFetchPreviousInstances =
          target.scrollTop === 0 &&
          instancesStore.shouldFetchPreviousInstances();
        const shouldFetchNextInstances =
          target.scrollHeight - target.clientHeight - target.scrollTop <= 0 &&
          instancesStore.shouldFetchNextInstances();

        if (shouldFetchNextInstances) {
          await instancesStore.fetchNextInstances();
        }
        if (shouldFetchPreviousInstances) {
          const shouldScrollDown =
            instancesStore.state.processInstances.length ===
            MAX_INSTANCES_STORED;
          await instancesStore.fetchPreviousInstances();
          if (
            shouldScrollDown &&
            instancesStore.state.latestFetch?.processInstancesCount !== 0
          ) {
            if (instancesStore.state.latestFetch !== null) {
              target.scrollTo(
                0,
                target.scrollTop +
                  instancesStore.state.latestFetch.processInstancesCount *
                    ROW_HEIGHT
              );
            }
          }
        }
      }}
    >
      <Styled.TableContainer ref={containerRef}>
        {(status === 'fetching' || status === 'refetching') && (
          <Styled.Spinner data-testid="instances-loader" />
        )}

        <Table>
          <Header />
          {['initial', 'first-fetch'].includes(status) && (
            <Skeleton {...props} rowsToDisplay={entriesPerPage} />
          )}
          {status === 'error' && <Message type="error" />}
          {status === 'refetching' ? null : areProcessInstancesEmpty ? (
            <Message type="empty" />
          ) : (
            <Instances />
          )}
        </Table>
      </Styled.TableContainer>
    </Styled.List>
  );
});

export default List;
