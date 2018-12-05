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
      filter,
      filterCount,
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
            filterCount={filterCount}
            filter={filter}
            sorting={this.props.sorting}
            onSort={this.props.onSort}
            expandState={this.props.expandState}
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
              onFirstElementChange={this.props.onFirstElementChange}
            />
          )}
        </SplitPane.Pane.Footer>
      </SplitPane.Pane>
    );
  }
}
