import React from 'react';

import Panel from 'modules/components/Panel';
import List from './List';
import ListFooter from './ListFooter';

import PropTypes from 'prop-types';

import {getData} from './api';

export default class ListView extends React.Component {
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
          <List
            data={this.state.instances}
            selection={this.props.selection}
            total={this.props.instancesInFilter}
            onEntriesPerPageChange={entriesPerPage =>
              this.setState({entriesPerPage})
            }
            onSelectionUpdate={this.props.onSelectionUpdate}
            filter={this.props.filter}
          />
        </Panel.Body>
        <Panel.Footer>
          <ListFooter
            total={this.props.instancesInFilter}
            perPage={this.state.entriesPerPage}
            firstElement={this.state.firstElement}
            onFirstElementChange={firstElement => this.setState({firstElement})}
            onAddToSelection={this.props.onAddToSelection}
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
