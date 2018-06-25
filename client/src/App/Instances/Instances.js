import React, {Component} from 'react';
import update from 'immutability-helper';
import {withRouter} from 'react-router';
import Header from '../Header';

import Panel from 'modules/components/Panel';
import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import ExpandButton from 'modules/components/ExpandButton';
import {EXPAND_CONTAINER} from 'modules/utils';

import PropTypes from 'prop-types';

import Filter from './Filter';
import ListView from './ListView';
import SelectionDisplay from './SelectionDisplay';

import {getCount} from './api';
import {parseFilterForRequest} from './service';
import * as Styled from './styled.js';

const {Pane} = SplitPane;

class Instances extends Component {
  static propTypes = {
    getState: PropTypes.func.isRequired,
    storeState: PropTypes.func.isRequired,
    location: PropTypes.object.isRequired,
    history: PropTypes.object.isRequired
  };

  constructor(props) {
    super(props);

    const {filter, filterCount, selections} = props.getState();
    const hasCachedFilterWithValues = filter && Object.keys(filter).length > 0;

    this.state = {
      filter: (hasCachedFilterWithValues && filter) || {
        active: true,
        incidents: true
      },
      filterCount: filterCount || 0,
      selection: this.createNewSelectionFragment(),
      selections: selections || [[]]
    };
  }

  createNewSelectionFragment = () => {
    return {query: {ids: new Set()}, exclusionList: new Set()};
  };

  handleAddToSelection = () => {
    const {selection} = this.state;
    this.setState(
      update(this.state, {
        selections: {
          0: {
            $push: [
              {
                query: {
                  ...selection.query,
                  ids: [...(selection.query.ids || [])]
                },
                exclusionList: [...selection.exclusionList]
              }
            ]
          }
        },
        selection: {$set: this.createNewSelectionFragment()}
      }),
      () => {
        this.props.storeState({selections: this.state.selections});
      }
    );
  };

  fetchFilterCount = async filter => {
    return await getCount(parseFilterForRequest(filter));
  };

  handleFilterChange = async change => {
    const filter = update(this.state.filter, change);

    this.setState({
      filter: filter,
      selection: this.createNewSelectionFragment()
    });

    this.props.storeState({filter});

    // separate setState to not block UI while waiting for server response
    const filterCount = await this.fetchFilterCount(filter);
    this.setState({filterCount});

    this.setFilterInURL();

    this.props.storeState({filterCount});
  };

  setFilterInURL = () => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: `?filter=${JSON.stringify(this.state.filter)}`
    });
  };

  async componentDidMount() {
    const filterCount = await this.fetchFilterCount(this.state.filter);

    this.setState({
      filterCount
    });

    this.setFilterInURL();
  }

  render() {
    const {instances, incidents} = this.props.getState();

    return (
      <div>
        <Header
          active="instances"
          instances={instances}
          filters={this.state.filterCount}
          selections={0} // needs a backend call because selections are complex
          incidents={incidents}
        />
        <Styled.Filter>
          <Styled.Left>
            <Panel isRounded>
              <Panel.Header isRounded>Filters</Panel.Header>
              <Panel.Body>
                <Filter
                  filter={this.state.filter}
                  onChange={this.handleFilterChange}
                />
              </Panel.Body>
              <ExpandButton
                containerId={EXPAND_CONTAINER.LEFT}
                isExpanded={true}
              />
              <Panel.Footer />
            </Panel>
          </Styled.Left>
          <Styled.Center>
            <Pane isRounded>
              <Pane.Header isRounded>Process Definition Name</Pane.Header>
              <Pane.Body>Process Definition Name content</Pane.Body>
            </Pane>
            <ListView
              instancesInFilter={this.state.filterCount}
              onSelectionUpdate={change => {
                this.setState({
                  selection: update(this.state.selection, change)
                });
              }}
              selection={this.state.selection}
              filter={this.state.filter}
              onAddToSelection={this.handleAddToSelection}
            />
          </Styled.Center>
          <Styled.Right>
            <Panel isRounded>
              <Panel.Header isRounded>Selections</Panel.Header>
              <Panel.Body>
                <SelectionDisplay selections={this.state.selections} />
              </Panel.Body>
              <ExpandButton
                containerId={EXPAND_CONTAINER.RIGHT}
                isExpanded={true}
              />
              <Panel.Footer />
            </Panel>
          </Styled.Right>
        </Styled.Filter>
      </div>
    );
  }
}

export default withRouter(withSharedState(Instances));
