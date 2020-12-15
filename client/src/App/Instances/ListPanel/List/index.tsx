/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState, useRef} from 'react';

import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import {Operations} from 'modules/components/Operations';
import StateIcon from 'modules/components/StateIcon';
import EmptyMessage from '../../EmptyMessage';

import {EXPAND_STATE, SORT_ORDER, DEFAULT_SORTING} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import ColumnHeader from './ColumnHeader';
import ListContext, {useListContext} from './ListContext';
import BaseSkeleton from './Skeleton';
import * as Styled from './styled';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {filtersStore} from 'modules/stores/filters';
import {observer} from 'mobx-react';
import {instancesStore, MAX_INSTANCES_STORED} from 'modules/stores/instances';
import {useNotifications} from 'modules/notifications';
import usePrevious from 'modules/hooks/usePrevious';
import {autorun} from 'mobx';

const {TBody, TD} = Table;
const ROW_HEIGHT = 38;

type ListProps = {
  data: WorkflowInstanceEntity[];
  Overlay?: any;
  isInitialDataLoaded: boolean;
  onSort?: () => void;
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
  children: React.ReactNode;
};
const List: React.FC<ListProps> = observer((props) => {
  let containerRef: any = React.createRef();
  let listRef: any = useRef();
  const prevExpandState = usePrevious(props.expandState);
  const [entriesPerPage, setEntriesPerPage] = useState(0);

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

  const shouldResetSorting = ({
    filter = filtersStore.state.filter,
    sorting = filtersStore.state.sorting,
  }) => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'canceled' does not exist on type '{}'.
    const isFinishedInFilter = filter.canceled || filter.completed;

    // reset sorting  by endDate when no finished filter is selected
    return !isFinishedInFilter && sorting.sortBy === 'endDate';
  };

  const handleSortingChange = (key: string) => {
    const prevSorting = filtersStore.state.sorting;

    const sorting = {
      sortBy: key,
      sortOrder:
        prevSorting.sortBy === key && prevSorting.sortOrder === SORT_ORDER.DESC
          ? SORT_ORDER.ASC
          : SORT_ORDER.DESC,
    };

    // check if sorting needs to be reset
    if (shouldResetSorting({sorting: sorting})) {
      return filtersStore.setSorting(DEFAULT_SORTING);
    }

    return filtersStore.setSorting(sorting);
  };

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
          // scroll positioning works well automatically but since we remove items manually after `max instances stored` limit is reached, it fails. so we need to position scroll by ourselves according to the previous state before next fetch.
          const shouldScrollUp =
            instancesStore.state.workflowInstances.length ===
            MAX_INSTANCES_STORED;

          await instancesStore.fetchNextInstances();

          if (shouldScrollUp) {
            if (instancesStore.state.latestFetch !== null) {
              target.scrollTo(
                0,
                target.scrollTop -
                  instancesStore.state.latestFetch.workflowInstancesCount *
                    ROW_HEIGHT
              );
            }
          }
        }
        if (shouldFetchPreviousInstances) {
          // scroll positioning works well automatically but since we remove items manually after `max instances stored` limit is reached, it fails. so we need to position scroll by ourselves according to the previous state before next fetch.
          const shouldScrollDown =
            instancesStore.state.workflowInstances.length ===
            MAX_INSTANCES_STORED;
          await instancesStore.fetchPreviousInstances();
          if (
            shouldScrollDown &&
            instancesStore.state.latestFetch?.workflowInstancesCount !== 0
          ) {
            if (instancesStore.state.latestFetch !== null) {
              target.scrollTo(
                0,
                target.scrollTop +
                  instancesStore.state.latestFetch.workflowInstancesCount *
                    ROW_HEIGHT
              );
            }
          }
        }
      }}
    >
      <Styled.TableContainer ref={containerRef}>
        {props.Overlay && props.Overlay()}

        <ListContext.Provider
          value={{
            data: props.data,
            onSort: handleSortingChange,
            rowsToDisplay: entriesPerPage,
            isInitialDataLoaded: props.isInitialDataLoaded,
          }}
        >
          <Table>{props.children}</Table>
        </ListContext.Provider>
      </Styled.TableContainer>
    </Styled.List>
  );
});

export default List;

const Skeleton = function (props: any) {
  const {rowsToDisplay} = useListContext();
  return <BaseSkeleton {...props} rowsToDisplay={rowsToDisplay} />;
};

type MessageProps = {
  message: string;
};

const Message: React.FC<MessageProps> = function ({message}) {
  return (
    <TBody>
      <Styled.EmptyTR>
        <TD colSpan={6}>
          <EmptyMessage
            message={message}
            data-testid="empty-message-instances-list"
          />
        </TD>
      </Styled.EmptyTR>
    </TBody>
  );
};

const Body = observer(function (props: any) {
  const {data} = useListContext();
  const notifications = useNotifications();

  return (
    <TBody {...props} data-testid="instances-list">
      {data.map((instance: any, idx: any) => {
        const isSelected = instanceSelectionStore.isInstanceChecked(
          instance.id
        );
        return (
          <Styled.TR key={idx} selected={isSelected}>
            <TD>
              <Styled.Cell>
                <Styled.SelectionStatusIndicator selected={isSelected} />
                <Checkbox
                  data-testid="instance-checkbox"
                  type="selection"
                  isChecked={isSelected}
                  onChange={() =>
                    instanceSelectionStore.selectInstance(instance.id)
                  }
                  title={`Select instance ${instance.id}`}
                />

                <StateIcon
                  state={instance.state}
                  data-testid={`${instance.state}-icon-${instance.id}`}
                />
                <Styled.WorkflowName>
                  {getWorkflowName(instance)}
                </Styled.WorkflowName>
              </Styled.Cell>
            </TD>
            <TD>
              <Styled.InstanceAnchor
                to={`/instances/${instance.id}`}
                title={`View instance ${instance.id}`}
              >
                {instance.id}
              </Styled.InstanceAnchor>
            </TD>
            <TD>{`Version ${instance.workflowVersion}`}</TD>
            <TD data-testid="start-time">{formatDate(instance.startDate)}</TD>
            <TD data-testid="end-time">{formatDate(instance.endDate)}</TD>
            <TD>
              <Operations
                instance={instance}
                selected={isSelected}
                onOperation={() =>
                  instancesStore.markInstancesWithActiveOperations({
                    ids: [instance.id],
                  })
                }
                onFailure={() => {
                  instancesStore.unmarkInstancesWithActiveOperations({
                    instanceIds: [instance.id],
                  });
                  notifications.displayNotification('error', {
                    headline: 'Operation could not be created',
                  });
                }}
              />
            </TD>
          </Styled.TR>
        );
      })}
    </TBody>
  );
});

const Header = observer(function (props: any) {
  const {data, onSort, isInitialDataLoaded} = useListContext();
  const {isAllChecked} = instanceSelectionStore.state;
  const {filter, sorting} = filtersStore.state;

  const isListEmpty = !isInitialDataLoaded || data.length === 0;
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'canceled' does not exist on type '{}'.
  const listHasFinishedInstances = filter.canceled || filter.completed;
  return (
    <Styled.THead {...props}>
      <Styled.TRHeader>
        <Styled.TH>
          <Styled.CheckAll shouldShowOffset={!isInitialDataLoaded}>
            {isInitialDataLoaded ? (
              <Checkbox
                // @ts-expect-error ts-migrate(2322) FIXME: Property 'disabled' does not exist on type 'Intrin... Remove this comment to see the full error message
                disabled={isListEmpty}
                isChecked={isAllChecked}
                onChange={instanceSelectionStore.selectAllInstances}
                title="Select all instances"
              />
            ) : (
              <BaseSkeleton.Checkbox />
            )}
          </Styled.CheckAll>
          <ColumnHeader
            disabled={isListEmpty}
            onSort={onSort}
            label="Workflow"
            sortKey="workflowName"
            sorting={sorting}
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Instance Id"
            onSort={onSort}
            sortKey="id"
            sorting={sorting}
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Version"
            onSort={onSort}
            sortKey="workflowVersion"
            sorting={sorting}
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Start Time"
            onSort={onSort}
            sortKey="startDate"
            sorting={sorting}
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty || !listHasFinishedInstances}
            label="End Time"
            onSort={onSort}
            sortKey="endDate"
            sorting={sorting}
          />
        </Styled.TH>
        <Styled.OperationsTH>
          <ColumnHeader disabled={isListEmpty} label="Operations" />
        </Styled.OperationsTH>
      </Styled.TRHeader>
    </Styled.THead>
  );
});

// @ts-expect-error ts-migrate(2339) FIXME: Property 'Message' does not exist on type 'typeof ... Remove this comment to see the full error message
List.Message = Message;
// @ts-expect-error ts-migrate(2339) FIXME: Property 'Body' does not exist on type 'typeof Lis... Remove this comment to see the full error message
List.Body = Body;
// @ts-expect-error ts-migrate(2339) FIXME: Property 'Header' does not exist on type 'typeof L... Remove this comment to see the full error message
List.Header = Header;
// @ts-expect-error ts-migrate(2339) FIXME: Property 'Skeleton' does not exist on type 'typeof... Remove this comment to see the full error message
List.Skeleton = Skeleton;
