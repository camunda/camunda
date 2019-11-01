/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {remove} from 'lodash';

import SpinnerSkeleton from 'modules/components/Skeletons';
import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import Actions from 'modules/components/Actions';
import StateIcon from 'modules/components/StateIcon';
import EmptyMessage from './../../EmptyMessage';

import {EXPAND_STATE} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';
import {withSelection} from 'modules/contexts/SelectionContext';

import ColumnHeader from './ColumnHeader';
import ListContext, {useListContext} from './ListContext';
import * as Styled from './styled';

const {THead, TBody, TH, TR, TD} = Table;

class List extends React.Component {
  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    Overlay: PropTypes.oneOfType([PropTypes.func, PropTypes.bool]),
    onSelectedInstancesUpdate: PropTypes.func.isRequired,
    onEntriesPerPageChange: PropTypes.func.isRequired,
    selectedInstances: PropTypes.shape({
      all: PropTypes.bool,
      excludeIds: PropTypes.array,
      ids: PropTypes.array
    }).isRequired,
    isDataLoaded: PropTypes.bool.isRequired,
    filterCount: PropTypes.number,
    filter: PropTypes.object,
    sorting: PropTypes.object,
    onSort: PropTypes.func,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    onActionButtonClick: PropTypes.func,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  constructor(props) {
    super(props);
    this.myRef = React.createRef();
    this.state = {
      rowsToDisplay: 9
    };
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

  handleSelectAll = (_, isChecked) => {
    this.props.onSelectedInstancesUpdate({
      all: isChecked,
      ids: [],
      excludeIds: []
    });
  };

  handleSelectInstance = instance => (_, isChecked) => {
    const {selectedInstances, filterCount} = this.props;

    let {all} = selectedInstances;
    let ids = [...selectedInstances.ids];
    let excludeIds = [...selectedInstances.excludeIds];

    let selectedIdArray = undefined;
    let checked = undefined;

    // isChecked === true => instance has been selected
    // isChecked === false => instance has been deselected

    if (all) {
      // reverse logic:
      // if (isChecked) => excludeIds.remove(instance) (include in selection)
      // if (!isChecked) => excludeIds.add(instance) (exclude from selection)
      checked = !isChecked;
      selectedIdArray = excludeIds; // use reference to excludeIds
    } else {
      // if (isChecked) => ids.add(instance) (include in selection)
      // if (!isChecked) => ids.remove(instance) (exclude from selection)
      checked = isChecked;
      selectedIdArray = ids; // use reference to ids
    }

    this.handleSelection(selectedIdArray, instance, checked);

    if (selectedIdArray.length === filterCount) {
      // selected array contains all filtered instances

      // (1) reset arrays in selection
      ids = [];
      excludeIds = [];

      // (2) determine 'all' state
      // if (!all) => all = true
      // if (all) => all = false
      all = !all;
    }

    this.props.onSelectedInstancesUpdate({all, ids, excludeIds});
  };

  handleSelection = (selectedIds = [], {id}, isChecked) => {
    if (isChecked) {
      selectedIds.push(id);
    } else {
      remove(selectedIds, elem => elem === id);
    }
  };

  isSelected = id => {
    const {selectedInstances} = this.props;
    const {all} = selectedInstances;
    return all ? !this.isExcluded(id) : this.isIncluded(id);
  };

  isExcluded = id => {
    const {selectedInstances} = this.props;
    const {excludeIds = []} = selectedInstances;
    return excludeIds.indexOf(id) >= 0;
  };

  isIncluded = id => {
    const {selectedInstances} = this.props;
    const {ids = []} = selectedInstances;
    return ids.indexOf(id) >= 0;
  };

  areAllInstancesSelected = () => {
    const {selectedInstances} = this.props;
    const {all, excludeIds = []} = selectedInstances;
    return all && excludeIds.length === 0;
  };

  recalculateHeight() {
    if (this.myRef.current) {
      const rows = ~~(this.myRef.current.clientHeight / 38) - 1;
      this.setState({rowsToDisplay: rows});
      this.props.onEntriesPerPageChange(rows);
    }
  }

  handleActionButtonClick = instance => {
    this.props.onActionButtonClick(instance);
  };

  render() {
    return (
      <Styled.List>
        <Styled.TableContainer ref={this.myRef}>
          {this.props.Overlay && this.props.Overlay()}

          <ListContext.Provider
            value={{
              data: this.props.data,
              filter: this.props.filter,
              sorting: this.props.sorting,
              onSort: this.props.onSort,
              areAllInstancesSelected: this.areAllInstancesSelected,
              handleSelectAll: this.handleSelectAll,
              rowsToDisplay: this.state.rowsToDisplay,
              isSelected: this.isSelected,
              isDataLoaded: this.props.isDataLoaded,
              handleSelectInstance: this.handleSelectInstance,
              handleActionButtonClick: this.handleActionButtonClick
            }}
          >
            <Table>{this.props.children}</Table>
          </ListContext.Provider>
        </Styled.TableContainer>
      </Styled.List>
    );
  }
}

const WrappedList = withSelection(List);
WrappedList.Item = List;

export default WrappedList;

WrappedList.Item.Skeleton = function Skeleton() {
  return (
    <TBody>
      <Styled.EmptyTR>
        <Styled.EmptyTD colSpan={6}>
          <SpinnerSkeleton />
        </Styled.EmptyTD>
      </Styled.EmptyTR>
    </TBody>
  );
};

WrappedList.Item.Message = class Message extends React.Component {
  static propTypes = {
    message: PropTypes.string
  };
  render() {
    return (
      <TBody>
        <Styled.EmptyTR>
          <TD colSpan={5}>
            <EmptyMessage
              message={this.props.message}
              data-test="empty-message-instances-list"
            />
          </TD>
        </Styled.EmptyTR>
      </TBody>
    );
  }
};

WrappedList.Item.Body = function Body(props) {
  const {
    data,
    rowsToDisplay,
    isSelected,
    handleSelectInstance,
    handleActionButtonClick
  } = useListContext();

  return (
    <TBody {...props}>
      {data.slice(0, rowsToDisplay).map((instance, idx) => (
        <TR key={idx} selected={isSelected(instance.id)}>
          <TD>
            <Styled.Cell>
              <Styled.SelectionStatusIndicator selected={false} />
              <Checkbox
                type="selection"
                isChecked={isSelected(instance.id)}
                onChange={handleSelectInstance(instance)}
                title={`Select instance ${instance.id}`}
              />

              <StateIcon state={instance.state} />
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
          <TD>{formatDate(instance.startDate)}</TD>
          <TD>{formatDate(instance.endDate)}</TD>
          <TD>
            <Actions
              instance={instance}
              selected={isSelected(instance.id)}
              onButtonClick={() => handleActionButtonClick(instance)}
            />
          </TD>
        </TR>
      ))}
    </TBody>
  );
};

WrappedList.Item.Header = function Header(props) {
  const {
    data,
    filter,
    sorting,
    onSort,
    areAllInstancesSelected,
    handleSelectAll,
    isDataLoaded
  } = useListContext();

  const isListEmpty = !isDataLoaded || data.length === 0;
  const listHasFinishedInstances = filter.canceled || filter.completed;
  return (
    <THead {...props}>
      <TR>
        <TH>
          <React.Fragment>
            <Styled.CheckAll>
              <Checkbox
                disabled={isListEmpty}
                isChecked={areAllInstancesSelected()}
                onChange={handleSelectAll}
                title="Select all instances"
              />
            </Styled.CheckAll>
            <ColumnHeader
              disabled={isListEmpty}
              onSort={onSort}
              label="Workflow"
              sortKey="workflowName"
              sorting={sorting}
            />
          </React.Fragment>
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
        <Styled.ActionsTH>
          <ColumnHeader disabled={isListEmpty} label="Actions" />
        </Styled.ActionsTH>
      </TR>
    </THead>
  );
};
