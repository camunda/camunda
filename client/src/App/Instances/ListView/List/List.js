import React from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';
import {EXPAND_STATE} from 'modules/constants';
import StateIcon from 'modules/components/StateIcon';

import {isEqual} from 'modules/utils';
import {areIdsArray, areIdsSet, getModifiedIdSet} from './service';

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
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE))
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

  getSelectionFilters = selection => {
    let selectionFilters = {};

    Object.keys(selection).forEach(key => {
      if (
        [
          'active',
          'incidents',
          'completed',
          'canceled',
          'startDateAfter',
          'startDateBefore',
          'errorMessage',
          'workflowIds'
        ].includes(key)
      ) {
        selectionFilters[key] = selection[key];
      }
    });
    return selectionFilters;
  };

  areAllInstancesSelected = () => {
    const {selection, filterCount, filter} = this.props;
    const selectionFilters = this.getSelectionFilters(selection);

    if (selection.excludeIds.size > 0) return false;
    if (
      isEqual(selectionFilters, filter) ||
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
    const selection = this.props.selection;
    const {excludeIds, ids} = selection;
    const selectionFilters = this.getSelectionFilters(selection);

    if (excludeIds.has(id)) return false;
    if (isEqual(selectionFilters, this.props.filter)) return true;
    if (
      (ids && !Array.isArray(ids) && ids.has(id)) ||
      (ids && Array.isArray(ids))
    )
      return true;

    return false;
  };

  onSelectionChange = instance => (event, isChecked) => {
    const {selection, filter} = this.props;

    const selectionFilters = this.getSelectionFilters(selection);
    const changeType = isEqual(selectionFilters, filter) ? 'excludeIds' : 'Ids';

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
          <TH>Actions</TH>
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
                <Styled.Selection>
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
                </Styled.Selection>
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
              <TD />
            </TR>
          ))}
      </TBody>
    );
  }

  render() {
    return (
      <Styled.List>
        <Styled.TableContainer innerRef={this.containerRef}>
          <Table>
            {this.renderTableHead()}
            {this.renderTableBody()}
          </Table>
        </Styled.TableContainer>
      </Styled.List>
    );
  }
}
