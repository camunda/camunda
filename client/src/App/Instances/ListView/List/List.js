import React from 'react';
import PropTypes from 'prop-types';
import {isEqual, isEmpty} from 'lodash';

import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import Actions from 'modules/components/Actions';
import StateIcon from 'modules/components/StateIcon';
import EmptyMessage from './../../EmptyMessage';

import {EXPAND_STATE} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import {
  areIdsArray,
  areIdsSet,
  getModifiedIdSet,
  createIdArrayFromFilterString
} from './service';

import HeaderSortIcon from './HeaderSortIcon';
import * as Styled from './styled';

const {THead, TBody, TH, TR, TD} = Table;
export default class List extends React.Component {
  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    onUpdateSelection: PropTypes.func.isRequired,
    onEntriesPerPageChange: PropTypes.func.isRequired,
    selection: PropTypes.shape({
      excludeIds: PropTypes.instanceOf(Set)
      // ,ids: PropTypes.oneOfType([PropTypes.Array, PropTypes.instanceOf(Set)])
    }).isRequired,
    filterCount: PropTypes.number,
    filter: PropTypes.object,
    sorting: PropTypes.object,
    handleSorting: PropTypes.func,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    isDataLoaded: PropTypes.bool
  };

  state = {
    rowsToDisplay: 9
  };

  componentDidMount() {
    this.recalculateHeight();
  }

  componentDidUpdate({expandState: prevExpandState}) {
    const {expandState} = this.props;

    // only call recalculateHeight if the expandedId changes and the pane is not collapsed
    if (
      prevExpandState !== expandState &&
      expandState !== EXPAND_STATE.COLLAPSED
    ) {
      this.recalculateHeight();
    }
  }

  getSelectionFilters = (selection, filter) => {
    let selectionFilters = {};

    // checks if selection filters are also available in the passed filters.
    Object.keys(selection).forEach(key => {
      if (filter.hasOwnProperty(key)) {
        selectionFilters[key] = selection[key];
      }
    });
    return selectionFilters;
  };

  areAllInstancesSelected = () => {
    const {selection, filterCount, filter} = this.props;
    const selectionFilters = this.getSelectionFilters(selection, filter);

    if (selection.excludeIds.size > 0) return false;

    if (typeof selection.ids === 'string') {
      selection.ids = createIdArrayFromFilterString(selection.ids);
    }
    if (
      (isEqual(selectionFilters, filter) && !isEmpty(filter)) ||
      areIdsArray(selection, filterCount) ||
      areIdsSet(selection, filterCount)
    )
      return true;

    return false;
  };

  handleToggleSelectAll = (event, isChecked) => {
    const selected = isChecked ? this.props.filter : {ids: new Set()};
    this.props.onUpdateSelection({...selected, excludeIds: new Set()});
  };

  isSelected = id => {
    const {filter} = this.props;
    const selection = this.props.selection;
    let {excludeIds, ids} = selection;
    const selectionFilters = this.getSelectionFilters(selection, filter);

    if (excludeIds.has(id)) return false;
    if (
      isEqual(selectionFilters, this.props.filter) &&
      !isEmpty(this.props.filter)
    )
      return true;

    if (typeof ids === 'string') {
      ids = createIdArrayFromFilterString(ids);
    }

    if (
      (ids && !Array.isArray(ids) && ids.has(id)) ||
      (ids && Array.isArray(ids))
    )
      return true;

    return false;
  };

  onSelectionChange = instance => (event, isChecked) => {
    const {selection, filter} = this.props;

    const selectionFilters = this.getSelectionFilters(selection, filter);
    const changeType =
      isEqual(selectionFilters, filter) && !isEmpty(filter)
        ? 'excludeIds'
        : 'Ids';

    const IdSetChanges =
      changeType === 'excludeIds'
        ? {isAdded: !isChecked, set: selection.excludeIds, id: instance.id}
        : {isAdded: isChecked, set: selection.ids, id: instance.id};

    const modifiedSet = getModifiedIdSet(IdSetChanges);

    this.props.onUpdateSelection(
      changeType === 'excludeIds'
        ? {...filter, ids: new Set(), excludeIds: modifiedSet}
        : {ids: modifiedSet, excludeIds: new Set()}
    );
  };

  recalculateHeight() {
    if (this.container) {
      const rows = ~~(this.container.clientHeight / 38) - 1;
      this.setState({rowsToDisplay: rows});
      this.props.onEntriesPerPageChange(rows);
    }
  }

  containerRef = node => {
    this.container = node;
  };

  renderTableHead() {
    return (
      <THead>
        <TR>
          <TH>
            <React.Fragment>
              <Styled.CheckAll>
                <Checkbox
                  disabled={!this.props.filterCount}
                  isChecked={this.areAllInstancesSelected()}
                  onChange={this.handleToggleSelectAll}
                  title="Select all instances"
                />
              </Styled.CheckAll>
              Workflow Definition
            </React.Fragment>
          </TH>
          <TH>
            Instance Id
            <HeaderSortIcon
              sortKey="id"
              sorting={this.props.sorting}
              handleSorting={this.props.handleSorting}
            />
          </TH>
          <TH>
            Start Time
            <HeaderSortIcon
              sortKey="startDate"
              sorting={this.props.sorting}
              handleSorting={this.props.handleSorting}
            />
          </TH>
          <TH>
            End Time
            <HeaderSortIcon
              sortKey="endDate"
              sorting={this.props.sorting}
              handleSorting={this.props.handleSorting}
            />
          </TH>
          <Styled.Th>Actions</Styled.Th>
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
                    onChange={this.onSelectionChange(instance)}
                    title={`Select instance ${instance.id}`}
                  />

                  <StateIcon instance={instance} />
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
              <TD>{formatDate(instance.startDate)}</TD>
              <TD>{formatDate(instance.endDate)}</TD>
              <TD>
                <Styled.Cell>
                  <Actions instance={instance} />
                </Styled.Cell>
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
        <Styled.TableContainer innerRef={this.containerRef}>
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
