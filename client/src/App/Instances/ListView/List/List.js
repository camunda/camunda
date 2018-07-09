import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import Checkbox from 'modules/components/Checkbox';
import StateIcon from 'modules/components/StateIcon';
import {EXPAND_STATE} from 'modules/constants';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import Table from 'modules/components/Table';

import * as Styled from './styled';

export default class List extends React.Component {
  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    onSelectionUpdate: PropTypes.func.isRequired,
    onEntriesPerPageChange: PropTypes.func.isRequired,
    selection: PropTypes.shape({
      exclusionList: PropTypes.instanceOf(Set),
      query: PropTypes.object,
      list: PropTypes.arrayOf(PropTypes.object)
    }).isRequired,
    total: PropTypes.number,
    filter: PropTypes.object,
    sortBy: PropTypes.object,
    handleSorting: PropTypes.func,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE))
  };

  state = {
    rowsToDisplay: null
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

  getTableConfig = () => {
    return {
      selectionCheck: ({id}) => this.isSelected(id),
      isSortable: {
        workflowId: false,
        id: true,
        startDate: true,
        endDate: true,
        actions: false
      },
      sortBy: this.props.sortBy
    };
  };

  areAllInstancesSelected = () => {
    const {
      selection: {query, exclusionList},
      total,
      filter
    } = this.props;

    if (exclusionList.size > 0) return false;
    if (query === filter) return true;
    if (query.ids && query.ids.size === total) return true;

    return false;
  };

  handleToggleSelectAll = isChecked => {
    const selected = isChecked ? {ids: new Set()} : this.props.filter;

    this.props.onSelectionUpdate({
      query: {$set: selected},
      exclusionList: {$set: new Set()}
    });
  };

  getInstanceAnchor = id => {
    return (
      <Styled.InstanceAnchor to={`/instances/${id}`}>
        {id}
      </Styled.InstanceAnchor>
    );
  };

  formatTableRow = instance => {
    return {
      data: {
        ...instance
      },
      view: {
        id: this.getInstanceAnchor(instance.id),
        workflowId: this.getSelection(instance),
        startDate: formatDate(instance.startDate),
        endDate: formatDate(instance.endDate)
      }
    };
  };

  isSelected = id => {
    const {query, exclusionList} = this.props.selection;
    if (exclusionList.has(id)) return false;
    if (query === this.props.filter) return true;
    if (query.ids && query.ids.has(id)) return true;

    return false;
  };

  findInSelectionList = id => {
    const {list} = this.props.selection;
    for (let instance of list) {
      if (instance.id === id) {
        return instance;
      }
    }
  };

  onSelectionChange = (isChecked, instance) => {
    const {selection, filter} = this.props;

    const updateOptions = [
      {exclusionList: {[isChecked ? '$add' : '$remove']: [instance.id]}},
      {query: {ids: {[isChecked ? '$remove' : '$add']: [instance.id]}}}
    ];

    const selectionUpdate =
      selection.query === filter ? updateOptions[0] : updateOptions[1];

    this.props.onSelectionUpdate(selectionUpdate);
  };

  getSelection = instance => {
    const isSelected = this.isSelected(instance.id);
    return (
      <Styled.Selection>
        <Styled.SelectionStatusIndicator selected={false} />
        <Checkbox
          type="selection"
          isChecked={isSelected}
          onChange={({isChecked}) => {
            this.onSelectionChange(isChecked, instance);
          }}
        />

        <StateIcon instance={instance} />
        <Styled.WorkflowName>{getWorkflowName(instance)}</Styled.WorkflowName>
      </Styled.Selection>
    );
  };

  recalculateHeight() {
    if (this.container) {
      const rows = ~~(this.container.clientHeight / 38) - 1;
      this.setState({rowsToDisplay: rows});
      this.props.onEntriesPerPageChange(rows);
    }
  }

  getTableHeaders = () => {
    return {
      workflowId: (
        <Fragment>
          <Styled.CheckAll>
            <Checkbox
              isChecked={this.areAllInstancesSelected()}
              onChange={({isChecked}) => this.handleToggleSelectAll(isChecked)}
            />
          </Styled.CheckAll>
          Workflow Definition
        </Fragment>
      ),
      id: 'Instance Id',
      startDate: 'Start Time',
      endDate: 'End Time',
      actions: 'Actions'
    };
  };

  getTableData = () => {
    return this.props.data
      .slice(0, this.state.rowsToDisplay)
      .map(this.formatTableRow);
  };

  containerRef = node => {
    this.container = node;
  };

  render() {
    return (
      <Styled.InstancesList>
        <Styled.TableContainer innerRef={this.containerRef}>
          {!this.state.rowsToDisplay || !this.props.data ? null : (
            <Table
              headers={this.getTableHeaders()}
              data={this.getTableData()}
              config={this.getTableConfig()}
              handleSorting={this.props.handleSorting}
            />
          )}
        </Styled.TableContainer>
      </Styled.InstancesList>
    );
  }
}
