import React from 'react';
import Table from 'modules/components/Table';

import * as Styled from './styled';

const headerConfig = {
  headerLabels: {
    workflowDefinitionId: 'Process Definition',
    id: 'Instance ID',
    startDate: 'Start Time',
    endDate: 'End Time',
    actions: 'Actions'
  },
  order: ['workflowDefinitionId', 'id', 'startDate', 'endDate', 'actions']
};

export default class InstancesList extends React.Component {
  state = {
    rowsToDisplay: null
  };

  render() {
    return (
      <Styled.TableContainer innerRef={node => (this.container = node)}>
        {this.renderTable()}
      </Styled.TableContainer>
    );
  }

  renderTable() {
    if (!this.state.rowsToDisplay || !this.props.data) {
      return null;
    }

    return (
      <Table
        data={this.props.data.slice(0, this.state.rowsToDisplay)}
        config={headerConfig}
      />
    );
  }

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
