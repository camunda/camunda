/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {remove} from 'lodash';

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
import * as Styled from './styled';

const {THead, TBody, TH, TR, TD} = Table;
class List extends React.Component {
  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    onSelectedInstancesUpdate: PropTypes.func.isRequired,
    onEntriesPerPageChange: PropTypes.func.isRequired,
    selectedInstances: PropTypes.shape({
      all: PropTypes.bool,
      excludeIds: PropTypes.array,
      ids: PropTypes.array
    }).isRequired,
    filterCount: PropTypes.number,
    filter: PropTypes.object,
    sorting: PropTypes.object,
    onSort: PropTypes.func,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    isDataLoaded: PropTypes.bool,
    onActionButtonClick: PropTypes.func
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

  renderTableHead() {
    const isListEmpty = this.props.data.length === 0;
    const listHasFinishedInstances =
      this.props.filter.canceled || this.props.filter.completed;

    return (
      <THead>
        <TR>
          <TH>
            <React.Fragment>
              <Styled.CheckAll>
                <Checkbox
                  disabled={isListEmpty}
                  isChecked={this.areAllInstancesSelected()}
                  onChange={this.handleSelectAll}
                  title="Select all instances"
                />
              </Styled.CheckAll>
              <ColumnHeader
                disabled={isListEmpty}
                onSort={this.props.onSort}
                label="Workflow"
                sortKey="workflowName"
                sorting={this.props.sorting}
              />
            </React.Fragment>
          </TH>
          <TH>
            <ColumnHeader
              disabled={isListEmpty}
              label="Instance Id"
              onSort={this.props.onSort}
              sortKey="id"
              sorting={this.props.sorting}
            />
          </TH>
          <TH>
            <ColumnHeader
              disabled={isListEmpty}
              label="Version"
              onSort={this.props.onSort}
              sortKey="workflowVersion"
              sorting={this.props.sorting}
            />
          </TH>
          <TH>
            <ColumnHeader
              disabled={isListEmpty}
              label="Start Time"
              onSort={this.props.onSort}
              sortKey="startDate"
              sorting={this.props.sorting}
            />
          </TH>
          <TH>
            <ColumnHeader
              disabled={isListEmpty || !listHasFinishedInstances}
              label="End Time"
              onSort={this.props.onSort}
              sortKey="endDate"
              sorting={this.props.sorting}
            />
          </TH>
          <Styled.ActionsTH>
            <ColumnHeader disabled={isListEmpty} label="Actions" />
          </Styled.ActionsTH>
        </TR>
      </THead>
    );
  }

  renderTableBody() {
    return (
      <TBody>
        {this.props.data
          .slice(0, this.state.rowsToDisplay)
          .map((instance, idx) => (
            <TR key={idx} selected={this.isSelected(instance.id)}>
              <TD>
                <Styled.Cell>
                  <Styled.SelectionStatusIndicator selected={false} />
                  <Checkbox
                    type="selection"
                    isChecked={this.isSelected(instance.id)}
                    onChange={this.handleSelectInstance(instance)}
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
                  selected={this.isSelected(instance.id)}
                  onButtonClick={this.handleActionButtonClick.bind(
                    this,
                    instance
                  )}
                />
              </TD>
            </TR>
          ))}
      </TBody>
    );
  }

  renderEmptyMessage = () => {
    return (
      <TBody>
        <Styled.EmptyTR>
          <TD colSpan={5}>
            <EmptyMessage
              message={this.getEmptyListMessage()}
              data-test="empty-message-instances-list"
            />
          </TD>
        </Styled.EmptyTR>
      </TBody>
    );
  };

  getEmptyListMessage = () => {
    const {active, incidents, completed, canceled} = this.props.filter;

    let msg = 'There are no instances matching this filter set.';

    if (!active && !incidents && !completed && !canceled) {
      msg += '\n To see some results, select at least one instance state.';
    }

    return msg;
  };

  render() {
    const isListEmpty = this.props.data.length === 0;

    return (
      <Styled.List>
        <Styled.TableContainer ref={this.myRef}>
          <Table>
            {this.renderTableHead()}
            {isListEmpty &&
              this.props.isDataLoaded &&
              this.renderEmptyMessage()}
            {this.renderTableBody()}
          </Table>
        </Styled.TableContainer>
      </Styled.List>
    );
  }
}

export default withSelection(List);
