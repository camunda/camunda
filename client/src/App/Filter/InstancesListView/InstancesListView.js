import React from 'react';

import Panel from 'modules/components/Panel';
import InstancesList from './InstancesList';
import InstancesListFooter from './InstancesListFooter';

import PropTypes from 'prop-types';

import {getData} from './api';

export default class InstancesListView extends React.Component {
  state = {
    firstElement: 0,
    instances: [],
    entriesPerPage: 0
  };

  static propTypes = {
    selection: PropTypes.shape({
      list: PropTypes.instanceOf(Set),
      isBlacklist: PropTypes.bool
    }).isRequired,
    instancesInFilter: PropTypes.number.isRequired,
    onSelectionUpdate: PropTypes.func.isRequired,
    filter: PropTypes.object.isRequired
  };

  render() {
    return (
      <Panel>
        <Panel.Header>Instances</Panel.Header>
        <Panel.Body>
          <InstancesList
            data={this.state.instances}
            selection={this.props.selection}
            total={this.props.instancesInFilter}
            onEntriesPerPageChange={entriesPerPage =>
              this.setState({entriesPerPage})
            }
            onSelectionUpdate={this.props.onSelectionUpdate}
          />
        </Panel.Body>
        <Panel.Footer>
          <InstancesListFooter
            total={this.props.instancesInFilter}
            perPage={this.state.entriesPerPage}
            firstElement={this.state.firstElement}
            onFirstElementChange={firstElement => this.setState({firstElement})}
          />
        </Panel.Footer>
      </Panel>
    );
  }

  componentDidMount() {
    this.loadData();
  }

  componentDidUpdate(prevProps, prevState) {
    if (
      prevProps.filter !== this.props.filter &&
      this.state.firstElement !== 0
    ) {
      return this.setState({firstElement: 0});
    }
    if (
      prevProps.filter !== this.props.filter ||
      prevState.firstElement !== this.state.firstElement
    ) {
      this.loadData();
    }
  }

  loadData = async () => {
    this.setState({
      instances: await getData(this.props.filter, this.state.firstElement, 50)
    });
  };
}
