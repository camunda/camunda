import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import {EXPAND_STATE} from 'modules/constants';

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
    instances: PropTypes.array.isRequired,
    sorting: PropTypes.object.isRequired,
    onSort: PropTypes.func.isRequired,
    firstElement: PropTypes.number.isRequired,
    onFirstElementChange: PropTypes.func.isRequired
  };

  state = {
    entriesPerPage: 0
  };

  render() {
    const {
      selection,
      filter,
      filterCount,
      onAddToOpenSelection,
      onAddNewSelection,
      onAddToSpecificSelection,
      onUpdateSelection,
      onSort,
      onFirstElementChange,
      ...paneProps
    } = this.props;

    const isListEmpty = this.props.instances.length === 0;

    return (
      <SplitPane.Pane {...paneProps} hasShiftableControls>
        <SplitPane.Pane.Header>Instances</SplitPane.Pane.Header>
        <Styled.PaneBody>
          <List
            data={this.props.instances}
            selection={selection}
            filterCount={filterCount}
            filter={filter}
            sorting={this.props.sorting}
            onSort={this.props.onSort}
            expandState={this.props.expandState}
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
              firstElement={this.props.firstElement}
              selection={this.props.selection}
              selections={this.props.selections}
              openSelection={this.props.openSelection}
              onAddToSpecificSelection={onAddToSpecificSelection}
              onAddToOpenSelection={onAddToOpenSelection}
              onAddNewSelection={onAddNewSelection}
              onFirstElementChange={this.props.onFirstElementChange}
            />
          )}
        </SplitPane.Pane.Footer>
      </SplitPane.Pane>
    );
  }
}
