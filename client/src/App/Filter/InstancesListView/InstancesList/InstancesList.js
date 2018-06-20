import React from 'react';
import PropTypes from 'prop-types';

import Table from 'modules/components/Table';
import Checkbox from 'modules/components/Checkbox';
import StateIcon from 'modules/components/StateIcon';

import * as Styled from './styled';
import {formatDate} from 'modules/utils';

export default class InstancesList extends React.Component {
  state = {
    rowsToDisplay: null
  };

  static propTypes = {
    data: PropTypes.arrayOf(PropTypes.object).isRequired,
    onSelectionUpdate: PropTypes.func.isRequired,
    onEntriesPerPageChange: PropTypes.func.isRequired,
    selection: PropTypes.shape({
      list: PropTypes.instanceOf(Set),
      isBlacklist: PropTypes.bool
    }).isRequired
  };

  render() {
    return (
      <Styled.InstancesList>
        <Styled.TableContainer innerRef={node => (this.container = node)}>
          {this.renderTable()}
        </Styled.TableContainer>
      </Styled.InstancesList>
    );
  }

  renderTable() {
    if (!this.state.rowsToDisplay || !this.props.data) {
      return null;
    }

    return (
      <Table
        data={this.props.data
          .slice(0, this.state.rowsToDisplay)
          .map(this.formatData)}
        config={this.getConfig()}
      />
    );
  }

  getConfig = () => {
    return {
      headerLabels: {
        workflowDefinitionId: (
          <React.Fragment>
            <Styled.CheckAll>
              <Checkbox
                checked={this.areAllInstancesSelected()}
                onChange={this.handleToggleSelectAll}
              />
            </Styled.CheckAll>
            Workflow Definition
          </React.Fragment>
        ),
        id: 'Instance ID',
        startDate: 'Start Time',
        endDate: 'End Time',
        actions: 'Actions'
      },
      order: ['workflowDefinitionId', 'id', 'startDate', 'endDate', 'actions'],
      selectionCheck: ({id}) => this.isSelected(id)
    };
  };

  areAllInstancesSelected = () => {
    const {
      selection: {list, isBlacklist},
      total
    } = this.props;

    return (
      (isBlacklist && list.size === 0) || (!isBlacklist && list.size === total)
    );
  };

  handleToggleSelectAll = ({target: {checked}}) => {
    this.props.onSelectionUpdate({
      isBlacklist: {$set: checked},
      list: {$set: new Set()}
    });
  };

  formatData = instance => {
    return {
      ...instance,
      workflowDefinitionId: this.addSelection(instance),
      startDate: formatDate(instance.startDate),
      endDate: formatDate(instance.endDate)
    };
  };

  isSelected = id => {
    const {list, isBlacklist} = this.props.selection;
    for (let instance of list) {
      if (instance.id === id) {
        return !isBlacklist;
      }
    }
    return isBlacklist;
  };

  findInSelectionList = id => {
    const {list} = this.props.selection;
    for (let instance of list) {
      if (instance.id === id) {
        return instance;
      }
    }
  };

  addSelection = instance => {
    const {isBlacklist} = this.props.selection;
    const isSelected = this.isSelected(instance.id);
    return (
      <React.Fragment>
        <Styled.SelectionStatusIndicator selected={isSelected} />
        <Checkbox
          checked={isSelected}
          onChange={evt => {
            const newState = evt.target.checked;
            if (isBlacklist) {
              if (newState) {
                this.props.onSelectionUpdate({
                  list: {$remove: [this.findInSelectionList(instance.id)]}
                });
              } else {
                this.props.onSelectionUpdate({list: {$add: [instance]}});
              }
            } else {
              if (newState) {
                this.props.onSelectionUpdate({list: {$add: [instance]}});
              } else {
                this.props.onSelectionUpdate({
                  list: {$remove: [this.findInSelectionList(instance.id)]}
                });
              }
            }
          }}
        />
        <StateIcon instance={instance} />
        {instance.workflowDefinitionId}
      </React.Fragment>
    );
  };

  componentDidMount() {
    this.recalculateHeight();
  }

  recalculateHeight() {
    if (this.container) {
      const rows = ~~(this.container.clientHeight / 38) - 1;
      this.setState({rowsToDisplay: rows});
      this.props.onEntriesPerPageChange(rows);
    }
  }
}
