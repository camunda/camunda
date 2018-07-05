import React, {Component, Fragment} from 'react';
import update from 'immutability-helper';
import Header from '../Header';

import Panel from 'modules/components/Panel';
import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import {ICON_DIRECTION} from 'modules/components/ExpandButton/constants';

import PropTypes from 'prop-types';

import Filter from './Filter/index';
import ListView from './ListView';
import SelectionDisplay from './SelectionDisplay';

import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {
  parseFilterForRequest,
  getFilterQueryString
} from 'modules/utils/filter';
import {parseQueryString, isEmpty, createNewSelectionFragment} from './service';
import {DEFAULT_FILTER, FILTER_TYPES} from 'modules/constants/filter';
import * as Styled from './styled.js';

class Instances extends Component {
  static propTypes = {
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    location: PropTypes.object.isRequired,
    history: PropTypes.object.isRequired
  };

  constructor(props) {
    super(props);
    const {filterCount, selections} = props.getStateLocally();

    this.state = {
      filter: {},
      filterCount: filterCount || 0,
      selection: createNewSelectionFragment(),
      selections: selections || [[]]
    };
  }

  async componentDidMount() {
    this.setFilterFromUrl();
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevProps.location.search !== this.props.location.search) {
      this.setFilterFromUrl();
    }
  }

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
        selection: {$set: createNewSelectionFragment()}
      }),
      () => {
        this.props.storeStateLocally({selections: this.state.selections});
      }
    );
  };

  setFilterFromUrl = () => {
    let {filter} = parseQueryString(this.props.location.search);

    // filter from URL was missing or invalid
    if (!filter) {
      // set default filter selection
      filter = DEFAULT_FILTER;
      this.setFilterInURL(filter);
    }

    // filter is valid
    this.setState({filter}, () => {
      this.handleFilterCount();
    });
  };

  handleFilterCount = async () => {
    const filterCount = await fetchWorkflowInstancesCount(
      parseFilterForRequest(this.state.filter)
    );

    this.setState({
      filterCount
    });

    // save filterCount to localStorage
    this.props.storeStateLocally({filterCount});
  };

  handleFilterChange = async newFilter => {
    const filter = {...this.state.filter, ...newFilter};
    this.setFilterInURL(filter);

    // write current filter selection to local storage
    this.props.storeStateLocally({filter: filter});
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  render() {
    const {instances, incidents: incidentsCount} = this.props.getStateLocally();
    const {active, incidents, canceled, completed} = this.state.filter;
    return (
      <div>
        <Header
          active="instances"
          instances={instances}
          filters={this.state.filterCount}
          selections={0} // needs a backend call because selections are complex
          incidents={incidentsCount}
        />
        <Styled.Filter>
          <Styled.Left>
            <Panel isRounded>
              <Panel.Header isRounded>Filters</Panel.Header>
              <Panel.Body>
                {!isEmpty(this.state.filter) && (
                  <Fragment>
                    <Filter
                      type={FILTER_TYPES.RUNNING}
                      filter={{
                        active,
                        incidents
                      }}
                      onChange={this.handleFilterChange}
                    />
                    <Filter
                      type={FILTER_TYPES.FINISHED}
                      filter={{
                        completed,
                        canceled
                      }}
                      onChange={this.handleFilterChange}
                    />
                  </Fragment>
                )}
              </Panel.Body>
              <Styled.LeftExpandButton
                iconDirection={ICON_DIRECTION.LEFT}
                isExpanded={true}
              />
              <Panel.Footer />
            </Panel>
          </Styled.Left>
          <Styled.Center>
            <SplitPane.Pane isRounded>
              <SplitPane.Pane.Header isRounded>
                Process Definition Name
              </SplitPane.Pane.Header>
              <SplitPane.Pane.Body>
                Process Definition Name content
              </SplitPane.Pane.Body>
            </SplitPane.Pane>
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
              <Styled.SelectionHeader isRounded>
                Selections
              </Styled.SelectionHeader>
              <Panel.Body>
                <SelectionDisplay selections={this.state.selections} />
              </Panel.Body>
              <Styled.RightExpandButton
                iconDirection={ICON_DIRECTION.RIGHT}
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

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
