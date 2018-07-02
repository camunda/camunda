import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import {PANE_STATE} from 'modules/components/SplitPane/Pane/constants';

import List from './List';
import ListFooter from './ListFooter';
import {isEmpty} from '../service';
import {parseFilterForRequest} from 'modules/utils/filter';
import {fetchWorkflowInstances} from 'modules/api/instances';

const {Pane} = SplitPane;
export default class ListView extends React.Component {
  static propTypes = {
    selection: PropTypes.shape({
      list: PropTypes.instanceOf(Set),
      isBlacklist: PropTypes.bool
    }).isRequired,
    instancesInFilter: PropTypes.number.isRequired,
    onSelectionUpdate: PropTypes.func.isRequired,
    filter: PropTypes.object.isRequired,
    onAddToSelection: PropTypes.func,
    paneState: PropTypes.oneOf(Object.values(PANE_STATE))
  };

  state = {
    firstElement: 0,
    instances: [],
    entriesPerPage: 0,
    sortBy: {id: 'desc'}
  };

  componentDidMount() {
    !isEmpty(this.props.filter) && this.loadData();
  }

  componentDidUpdate(prevProps, prevState) {
    // set firstElement to 0 when filter changes
    if (
      prevProps.filter !== this.props.filter &&
      this.state.firstElement !== 0
    ) {
      return this.setState({firstElement: 0});
    }

    // load data when either filter or firstElement changes
    const key = Object.keys(prevState.sortBy)[0];
    const hasSortByChanged = prevState.sortBy[key] !== this.state.sortBy[key];
    if (
      prevProps.filter !== this.props.filter ||
      prevState.firstElement !== this.state.firstElement ||
      hasSortByChanged
    ) {
      this.loadData();
    }
  }

  loadData = async () => {
    const instances = await fetchWorkflowInstances(
      {
        ...parseFilterForRequest(this.props.filter),
        sortBy: this.state.sortBy
      },
      this.state.firstElement,
      50
    );

    this.setState({instances});
  };

  handleSorting = key => {
    const {sortBy} = this.state;
    const sortedKey = Object.keys(sortBy)[0];
    let newsortBy;
    if (!sortBy[key]) {
      newsortBy = {[key]: 'desc'};
    } else {
      const order = sortBy[sortedKey] === 'desc' ? 'asc' : 'desc';
      newsortBy = {[sortedKey]: order};
    }
    return this.setState({sortBy: newsortBy});
  };

  render() {
    return (
      <Pane {...this.props}>
        <Pane.Header>Instances</Pane.Header>
        <Pane.Body>
          {!isEmpty(this.props.filter) && (
            <List
              data={this.state.instances}
              selection={this.props.selection}
              total={this.props.instancesInFilter}
              onEntriesPerPageChange={entriesPerPage =>
                this.setState({entriesPerPage})
              }
              onSelectionUpdate={this.props.onSelectionUpdate}
              filter={this.props.filter}
              paneState={this.props.paneState}
              sortBy={this.state.sortBy}
              handleSorting={this.handleSorting}
            />
          )}
        </Pane.Body>
        <Pane.Footer>
          {!isEmpty(this.props.filter) && (
            <ListFooter
              total={this.props.instancesInFilter}
              perPage={this.state.entriesPerPage}
              firstElement={this.state.firstElement}
              onFirstElementChange={firstElement =>
                this.setState({firstElement})
              }
              onAddToSelection={this.props.onAddToSelection}
            />
          )}
        </Pane.Footer>
      </Pane>
    );
  }
}
