import React, {Component} from 'react';
import update from 'immutability-helper';

import Header from '../Header';

import Panel from 'modules/components/Panel';
import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';

import PropTypes from 'prop-types';

import Filter from './Filter';
import ListView from './ListView';
import SelectionDisplay from './SelectionDisplay';

import {getCount} from './api';
import {parseFilterForRequest} from './service';
import * as Styled from './styled.js';

const {Pane} = SplitPane;

export default withSharedState(
  class Instances extends Component {
    static propTypes = {
      getState: PropTypes.func.isRequired,
      storeState: PropTypes.func.isRequired
    };

    constructor(props) {
      super(props);

      const {filter, filterCount, selections} = props.getState();

      this.state = {
        filter: filter || {active: true, incidents: true},
        filterCount: filterCount || 0,
        selection: this.createNewSelectionFragment(),
        selections: selections || [[]]
      };
    }

    createNewSelectionFragment = () => {
      return {query: {ids: new Set()}, exclusionList: new Set()};
    };

    handleResetFilter = () => {
      const filter = update(this.state.filter, {
        active: {$set: !this.state.filter.active},
        incidents: {$set: !this.state.filter.incidents}
      });

      this.setState({
        filter
      });
    };

    handleFilterChange = type => async () => {
      const filter = update(this.state.filter, {
        [`${type}`]: {$set: !this.state.filter[type]}
      });

      this.setState({
        filter,
        selection: this.createNewSelectionFragment()
      });

      this.props.storeState({filter});

      // separate setState to not block UI while waiting for server response
      const filterCount = await getCount(filter);
      this.setState({filterCount});
      this.props.storeState({filterCount});
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
                <Panel.Header isRounded foldButtonType="left">
                  Filters
                </Panel.Header>
                <Panel.Body>
                  <Filter
                    filter={this.state.filter}
                    onChange={this.handleFilterChange}
                    onResetFilter={this.handleResetFilter}
                  />
                </Panel.Body>
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
                <Panel.Header isRounded foldButtonType="right">
                  Selections
                </Panel.Header>
                <Panel.Body>
                  <SelectionDisplay selections={this.state.selections} />
                </Panel.Body>
                <Panel.Footer />
              </Panel>
            </Styled.Right>
          </Styled.Filter>
        </div>
      );
    }

    async componentDidMount() {
      this.setState({
        filterCount: await getCount(parseFilterForRequest(this.state.filter))
      });
    }
  }
);
