import React from 'react';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';
import List from './List';
import ListFooter from './ListFooter';

import {parseFilterForRequest, isEmpty} from '../service';
import {getData} from './api';

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
    onAddToSelection: PropTypes.func
  };

  state = {
    firstElement: 0,
    instances: [],
    entriesPerPage: 0
  };

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
      instances: await getData(
        parseFilterForRequest(this.props.filter),
        this.state.firstElement,
        50
      )
    });
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
