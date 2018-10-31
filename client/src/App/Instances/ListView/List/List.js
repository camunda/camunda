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

import HeaderSortIcon from './HeaderSortIcon';
import * as Styled from './styled';

const {THead, TBody, TH, TR, TD} = Table;
export default class List extends React.Component {
  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    onUpdateSelection: PropTypes.func.isRequired,
    onEntriesPerPageChange: PropTypes.func.isRequired,
    selection: PropTypes.shape({
      all: PropTypes.bool,
      excludeIds: PropTypes.Array,
      ids: PropTypes.Array
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

  handleSelectAll = (_, isChecked) => {
    this.props.onUpdateSelection({
      all: isChecked,
      ids: [],
      excludeIds: []
    });
  };

  handleSelectInstance = instance => (_, isChecked) => {
    const {selection, filterCount} = this.props;

    let {all} = selection;
    let ids = [...selection.ids];
    let excludeIds = [...selection.excludeIds];

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

    this.props.onUpdateSelection({all, ids, excludeIds});
  };

  handleSelection = (selectedIds = [], {id}, isChecked) => {
    if (isChecked) {
      selectedIds.push(id);
    } else {
      remove(selectedIds, elem => elem === id);
    }
  };

  isSelected = id => {
    const {selection} = this.props;
    const {all} = selection;
    return all ? !this.isExcluded(id) : this.isIncluded(id);
  };

  isExcluded = id => {
    const {selection} = this.props;
    const {excludeIds = []} = selection;
    return excludeIds.indexOf(id) >= 0;
  };

  isIncluded = id => {
    const {selection} = this.props;
    const {ids = []} = selection;
    return ids.indexOf(id) >= 0;
  };

  areAllInstancesSelected = () => {
    const {selection} = this.props;
    const {all, excludeIds = []} = selection;
    return all && excludeIds.length === 0;
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
    const isListEmpty = this.props.data.length === 0;
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
              Workflow
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
                    onChange={this.handleSelectInstance(instance)}
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
                <Actions
                  instance={instance}
                  selected={this.isSelected(instance.id)}
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
        <Styled.TableContainer ref={this.containerRef}>
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
