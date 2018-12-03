import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import {EXPAND_STATE, SORT_ORDER, DEFAULT_SORTING} from 'modules/constants';
import {isEqual} from 'lodash';

import List from './List';
import ListFooter from './ListFooter';
import * as Styled from './styled';

export default class ListView extends React.Component {
  static propTypes = {
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    filter: PropTypes.object.isRequired,
    filterCount: PropTypes.number.isRequired,
    selections: PropTypes.array,
    onAddNewSelection: PropTypes.func,
    onAddToSpecificSelection: PropTypes.func,
    onAddToOpenSelection: PropTypes.func,
    onUpdateSelection: PropTypes.func.isRequired,
    openSelection: PropTypes.number,
    selection: PropTypes.object.isRequired,
    instancesLoaded: PropTypes.bool,
    instances: PropTypes.array
  };

  state = {
    entriesPerPage: 0,
    firstElement: 0,
    sorting: DEFAULT_SORTING
  };

  async componentDidUpdate(prevProps, prevState) {
    const hasFilterChanged = !isEqual(prevProps.filter, this.props.filter);
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortingChanged = !isEqual(prevState.sorting, this.state.sorting);
    const listHasFinishedInstances =
      this.props.filter.canceled || this.props.filter.completed;

    // reset sorting  before fetching, if sortBy is endDate and list has no finished instances
    if (!listHasFinishedInstances && this.state.sorting.sortBy === 'endDate') {
      return this.setState({sorting: DEFAULT_SORTING});
    }

    // set firstElement to 0 when filter changes
    if (hasFilterChanged && this.state.firstElement !== 0) {
      return this.setState({firstElement: 0});
    }

    if (hasFirstElementChanged || hasSortingChanged) {
      await this.props.fetchWorkflowInstances(
        this.state.sorting,
        this.state.firstElement
      );
    }
  }

  // (1) should make state sort order asc if key is currently sorted by in desc order
  handleSorting = key => {
    const {
      sorting: {sortBy: currentSortBy, sortOrder: currentSortOrder}
    } = this.state;

    let newSorting = {sortBy: key, sortOrder: SORT_ORDER.DESC};

    if (currentSortBy === key && currentSortOrder === SORT_ORDER.DESC) {
      newSorting.sortOrder = SORT_ORDER.ASC;
    }

    return this.setState({sorting: newSorting});
  };

  render() {
    const {
      selection,
      filter,
      expandState,
      filterCount,
      onAddToOpenSelection,
      onAddNewSelection,
      onAddToSpecificSelection,
      onUpdateSelection
    } = this.props;

    const isListEmpty = this.props.instances.length === 0;

    return (
      <SplitPane.Pane {...this.props} hasShiftableControls>
        <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <List
            data={this.props.instances}
            selection={selection}
            filterCount={filterCount}
            filter={filter}
            expandState={expandState}
            sorting={this.state.sorting}
            onSort={this.handleSorting}
            onUpdateSelection={onUpdateSelection}
            onEntriesPerPageChange={entriesPerPage =>
              this.setState({entriesPerPage})
            }
            isDataLoaded={this.props.instancesLoaded}
          />
        </Styled.PaneBody>
        <SplitPane.Pane.Footer>
          {!isListEmpty && (
            <ListFooter
              filterCount={filterCount}
              perPage={this.state.entriesPerPage}
              firstElement={this.state.firstElement}
              selection={this.props.selection}
              selections={this.props.selections}
              openSelection={this.props.openSelection}
              onAddToSpecificSelection={onAddToSpecificSelection}
              onAddToOpenSelection={onAddToOpenSelection}
              onAddNewSelection={onAddNewSelection}
              onFirstElementChange={firstElement =>
                this.setState({firstElement})
              }
            />
          )}
        </SplitPane.Pane.Footer>
      </SplitPane.Pane>
    );
  }
}
