/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

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

const {THead, TBody, TH, TR, TD} = Table;

type ListProps = {
  data: unknown;
  Overlay?: any;
  isDataLoaded: boolean;
  onSort?: () => void;
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
  onOperationButtonClick?: (...args: unknown[]) => void;
  children: React.ReactNode;
};

const List = observer(
  class List extends React.Component<ListProps, {}> {
    containerRef: any;

    constructor(props: any) {
      super(props);
      this.containerRef = React.createRef();
    }

    componentDidMount() {
      this.recalculateHeight();
    }

    componentDidUpdate(prevProps: ListProps) {
      const {expandState} = this.props;

      // only call recalculateHeight if the expandedId changes and the pane is not collapsed
      if (
        prevProps.expandState !== expandState &&
        expandState !== EXPAND_STATE.COLLAPSED
      ) {
        this.recalculateHeight();
      }
    }

    recalculateHeight() {
      if (this.containerRef.current?.clientHeight > 0) {
        const rows = ~~(this.containerRef.current.clientHeight / 38) - 1;
        filtersStore.setEntriesPerPage(rows);
      }
    }

    handleOperationButtonClick = (instance: any) => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'onOperationButtonClick' does not exist o... Remove this comment to see the full error message
      this.props.onOperationButtonClick(instance);
    };

    shouldResetSorting = ({
      filter = filtersStore.state.filter,
      sorting = filtersStore.state.sorting,
    }) => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'canceled' does not exist on type '{}'.
      const isFinishedInFilter = filter.canceled || filter.completed;

      // reset sorting  by endDate when no finished filter is selected
      return !isFinishedInFilter && sorting.sortBy === 'endDate';
    };

    handleSortingChange = (key: any) => {
      const prevSorting = filtersStore.state.sorting;

      const sorting = {
        sortBy: key,
        sortOrder:
          prevSorting.sortBy === key &&
          prevSorting.sortOrder === SORT_ORDER.DESC
            ? SORT_ORDER.ASC
            : SORT_ORDER.DESC,
      };

      // check if sorting needs to be reset
      if (this.shouldResetSorting({sorting: sorting})) {
        return filtersStore.setSorting(DEFAULT_SORTING);
      }

      return filtersStore.setSorting(sorting);
    };

    render() {
      return (
        <Styled.List>
          <Styled.TableContainer ref={this.containerRef}>
            {this.props.Overlay && this.props.Overlay()}

            <ListContext.Provider
              value={{
                data: this.props.data,
                onSort: this.handleSortingChange,
                rowsToDisplay: filtersStore.state.entriesPerPage,
                isDataLoaded: this.props.isDataLoaded,
                handleOperationButtonClick: this.handleOperationButtonClick,
              }}
            >
              <Table>{this.props.children}</Table>
            </ListContext.Provider>
          </Styled.TableContainer>
        </Styled.List>
      );
    }
  }
);

export default List;

const Skeleton = function (props: any) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'rowsToDisplay' does not exist on type 'u... Remove this comment to see the full error message
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
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'data' does not exist on type 'unknown'.
  const {data, rowsToDisplay, handleOperationButtonClick} = useListContext();

  return (
    <TBody {...props} data-testid="instances-list">
      {data.slice(0, rowsToDisplay).map((instance: any, idx: any) => {
        const isSelected = instanceSelectionStore.isInstanceChecked(
          instance.id
        );
        return (
          <TR key={idx} selected={isSelected}>
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
                onButtonClick={() => handleOperationButtonClick(instance)}
              />
            </TD>
          </TR>
        );
      })}
    </TBody>
  );
});

const Header = observer(function (props: any) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'data' does not exist on type 'unknown'.
  const {data, onSort, isDataLoaded} = useListContext();
  const {isAllChecked} = instanceSelectionStore.state;
  const {filter, sorting} = filtersStore.state;

  const isListEmpty = !isDataLoaded || data.length === 0;
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'canceled' does not exist on type '{}'.
  const listHasFinishedInstances = filter.canceled || filter.completed;
  return (
    <THead {...props}>
      <Styled.TR>
        <TH>
          <Styled.CheckAll shouldShowOffset={!isDataLoaded}>
            {isDataLoaded ? (
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
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Instance Id"
            onSort={onSort}
            sortKey="id"
            sorting={sorting}
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Version"
            onSort={onSort}
            sortKey="workflowVersion"
            sorting={sorting}
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty}
            label="Start Time"
            onSort={onSort}
            sortKey="startDate"
            sorting={sorting}
          />
        </TH>
        <TH>
          <ColumnHeader
            disabled={isListEmpty || !listHasFinishedInstances}
            label="End Time"
            onSort={onSort}
            sortKey="endDate"
            sorting={sorting}
          />
        </TH>
        <Styled.OperationsTH>
          <ColumnHeader disabled={isListEmpty} label="Operations" />
        </Styled.OperationsTH>
      </Styled.TR>
    </THead>
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
