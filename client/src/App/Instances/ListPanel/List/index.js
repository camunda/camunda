/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import Operations from 'modules/components/Operations';
import StateIcon from 'modules/components/StateIcon';
import EmptyMessage from '../../EmptyMessage';

import {EXPAND_STATE, SORT_ORDER, DEFAULT_SORTING} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import ColumnHeader from './ColumnHeader';
import ListContext, {useListContext} from './ListContext';
import BaseSkeleton from './Skeleton';
import * as Styled from './styled';
import {instanceSelection} from 'modules/stores/instanceSelection';
import {filters} from 'modules/stores/filters';
import {observer} from 'mobx-react';

const {THead, TBody, TH, TR, TD} = Table;

const List = observer(
  class List extends React.Component {
    static propTypes = {
      data: PropTypes.arrayOf(PropTypes.object).isRequired,
      Overlay: PropTypes.oneOfType([PropTypes.func, PropTypes.bool]),
      isDataLoaded: PropTypes.bool.isRequired,
      onSort: PropTypes.func,
      expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
      onOperationButtonClick: PropTypes.func,
      children: PropTypes.oneOfType([
        PropTypes.arrayOf(PropTypes.node),
        PropTypes.node,
      ]),
    };

    constructor(props) {
      super(props);
      this.containerRef = React.createRef();
    }

    componentDidMount() {
      this.recalculateHeight();
    }

    componentDidUpdate(prevProps) {
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
        filters.setEntriesPerPage(rows);
      }
    }

    handleOperationButtonClick = (instance) => {
      this.props.onOperationButtonClick(instance);
    };

    shouldResetSorting = ({
      filter = filters.state.filter,
      sorting = filters.state.sorting,
    }) => {
      const isFinishedInFilter = filter.canceled || filter.completed;

      // reset sorting  by endDate when no finished filter is selected
      return !isFinishedInFilter && sorting.sortBy === 'endDate';
    };

    handleSortingChange = (key) => {
      const prevSorting = filters.state.sorting;

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
        return filters.setSorting(DEFAULT_SORTING);
      }

      return filters.setSorting(sorting);
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
                rowsToDisplay: filters.state.entriesPerPage,
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

const Skeleton = function (props) {
  const {rowsToDisplay} = useListContext();
  return <BaseSkeleton {...props} rowsToDisplay={rowsToDisplay} />;
};

const Message = function ({message}) {
  return (
    <TBody>
      <Styled.EmptyTR>
        <TD colSpan={6}>
          <EmptyMessage
            message={message}
            data-test="empty-message-instances-list"
          />
        </TD>
      </Styled.EmptyTR>
    </TBody>
  );
};

Message.propTypes = {
  message: PropTypes.string,
};

const Body = observer(function (props) {
  const {data, rowsToDisplay, handleOperationButtonClick} = useListContext();

  return (
    <TBody {...props} data-test="instances-list">
      {data.slice(0, rowsToDisplay).map((instance, idx) => {
        const isSelected = instanceSelection.isInstanceChecked(instance.id);
        return (
          <TR key={idx} selected={isSelected}>
            <TD>
              <Styled.Cell>
                <Styled.SelectionStatusIndicator selected={isSelected} />
                <Checkbox
                  data-test="instance-checkbox"
                  type="selection"
                  isChecked={isSelected}
                  onChange={() => instanceSelection.selectInstance(instance.id)}
                  title={`Select instance ${instance.id}`}
                />

                <StateIcon
                  state={instance.state}
                  data-test={`${instance.state}-icon-${instance.id}`}
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
            <TD data-test="start-time">{formatDate(instance.startDate)}</TD>
            <TD data-test="end-time">{formatDate(instance.endDate)}</TD>
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

const Header = observer(function (props) {
  const {data, onSort, isDataLoaded} = useListContext();
  const {isAllChecked} = instanceSelection.state;
  const {filter, sorting} = filters.state;

  const isListEmpty = !isDataLoaded || data.length === 0;
  const listHasFinishedInstances = filter.canceled || filter.completed;
  return (
    <THead {...props}>
      <Styled.TR>
        <TH>
          <Styled.CheckAll shouldShowOffset={!isDataLoaded}>
            {isDataLoaded ? (
              <Checkbox
                disabled={isListEmpty}
                isChecked={isAllChecked}
                onChange={instanceSelection.selectAllInstances}
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

List.Message = Message;
List.Body = Body;
List.Header = Header;
List.Skeleton = Skeleton;
