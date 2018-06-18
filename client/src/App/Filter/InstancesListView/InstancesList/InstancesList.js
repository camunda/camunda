import React from 'react';
import Table from 'modules/components/Table';
import Checkbox from 'modules/components/Checkbox';
import StateIcon from 'modules/components/StateIcon';

import * as Styled from './styled';
import {formatDate} from 'modules/utils';

export default class InstancesList extends React.Component {
  state = {
    rowsToDisplay: null
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
              <Checkbox checked={false} onChange={() => {}} />
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

  addSelection = instance => {
    const {list, isBlacklist} = this.props.selection;
    const isSelected =
      (isBlacklist && !list.has(instance)) ||
      (!isBlacklist && list.has(instance));
    return (
      <React.Fragment>
        <Styled.SelectionStatusIndicator selected={isSelected} />
        <Checkbox
          checked={isSelected}
          onChange={evt => {
            const newState = evt.target.checked;
            if (isBlacklist) {
              if (newState) {
                this.props.updateSelection({list: {$remove: [instance]}});
              } else {
                this.props.updateSelection({list: {$add: [instance]}});
              }
            } else {
              if (newState) {
                this.props.updateSelection({list: {$add: [instance]}});
              } else {
                this.props.updateSelection({list: {$remove: [instance]}});
              }
            }
          }}
        />
        <StateIcon stateName={instance.state} />
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
      this.props.updateEntriesPerPage(rows);
    }
  }
}
