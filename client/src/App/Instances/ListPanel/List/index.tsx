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
import {EXPAND_STATE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';
import ColumnHeader from './ColumnHeader';
import ListContext, {useListContext} from './ListContext';
import BaseSkeleton from './Skeleton';
import * as Styled from './styled';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {observer} from 'mobx-react';
import {instancesStore, MAX_INSTANCES_STORED} from 'modules/stores/instances';
import {useNotifications} from 'modules/notifications';
import usePrevious from 'modules/hooks/usePrevious';
import {autorun} from 'mobx';
import {Locations} from 'modules/routes';
import {getFilters} from 'modules/utils/filter';
import {useLocation} from 'react-router-dom';

const {TBody, TD} = Table;
const ROW_HEIGHT = 38;

type ListProps = {
  data: ProcessInstanceEntity[];
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
            instancesStore.state.processInstances.length ===
            MAX_INSTANCES_STORED;

          await instancesStore.fetchNextInstances();

          if (shouldScrollUp) {
            if (instancesStore.state.latestFetch !== null) {
              target.scrollTo(
                0,
                target.scrollTop -
                  instancesStore.state.latestFetch.processInstancesCount *
                    ROW_HEIGHT
              );
            }
          }
        }
        if (shouldFetchPreviousInstances) {
          // scroll positioning works well automatically but since we remove items manually after `max instances stored` limit is reached, it fails. so we need to position scroll by ourselves according to the previous state before next fetch.
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
        {props.Overlay && props.Overlay()}

        <ListContext.Provider
          value={{
            data: props.data,
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
                <Styled.ProcessName>
                  {getProcessName(instance)}
                </Styled.ProcessName>
              </Styled.Cell>
            </TD>
            <TD>
              <Styled.InstanceAnchor
                to={(location) => Locations.instance(instance.id, location)}
                title={`View instance ${instance.id}`}
              >
                {instance.id}
              </Styled.InstanceAnchor>
            </TD>
            <TD>{`Version ${instance.processVersion}`}</TD>
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
  const {data, isInitialDataLoaded} = useListContext();
  const {isAllChecked} = instanceSelectionStore.state;
  const location = useLocation();
  const {canceled, completed} = getFilters(location.search);
  const isListEmpty = !isInitialDataLoaded || data.length === 0;
  const listHasFinishedInstances = canceled || completed;

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
            label="Process"
            sortKey="processName"
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Instance Id"
            sortKey="id"
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Version"
            sortKey="processVersion"
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Start Time"
            sortKey="startDate"
          />
        </Styled.TH>
        <Styled.TH>
          <ColumnHeader
            disabled={isListEmpty || !listHasFinishedInstances}
            label="End Time"
            sortKey="endDate"
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
