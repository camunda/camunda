import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import {EXPAND_STATE} from 'modules/constants/splitPane';
import {isEmpty} from 'modules/utils';

import List from './List';
import ListFooter from './ListFooter';
import {parseFilterForRequest} from 'modules/utils/filter';
import {fetchWorkflowInstances} from 'modules/api/instances';
import {ORDER, DEFAULT_SORT_BY} from 'modules/constants/sort';

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
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE))
  };

  state = {
    firstElement: 0,
    instances: [],
    entriesPerPage: 0,
    sortBy: DEFAULT_SORT_BY
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

    const prevSortKey = Object.keys(prevState.sortBy)[0];
    const hasFilterChanged = prevProps.filter !== this.props.filter;
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortByChanged =
      prevState.sortBy[prevSortKey] !== this.state.sortBy[prevSortKey];

    if (hasFilterChanged || hasFirstElementChanged || hasSortByChanged) {
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

  // (1) should make state sort order asc if key is currently sorted by in desc order
  handleSorting = key => {
    const isCurrentSortBy = !!this.state.sortBy[key];
    let newSortBy = {[key]: ORDER.DESC};

    if (isCurrentSortBy && this.state.sortBy[key] === ORDER.DESC) {
      newSortBy[key] = ORDER.ASC;
    }

    return this.setState({sortBy: newSortBy});
  };

  render() {
    return (
      <SplitPane.Pane {...this.props}>
        <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>
        <SplitPane.Pane.Body>
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
              expandState={this.props.expandState}
              sortBy={this.state.sortBy}
              handleSorting={this.handleSorting}
            />
          )}
        </SplitPane.Pane.Body>
        <SplitPane.Pane.Footer>
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
        </SplitPane.Pane.Footer>
      </SplitPane.Pane>
    );
  }
}
