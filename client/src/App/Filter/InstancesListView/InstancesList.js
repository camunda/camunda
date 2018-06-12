import React from 'react';
import {Table} from 'components';

import * as Styled from './styled';

const headerConfig = {
  headerLabels: {
    id: 'Instance ID',
    name: 'Process Definition Name'
  },
  order: ['id', 'name']
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
    if (!this.state.rowsToDisplay) {
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
