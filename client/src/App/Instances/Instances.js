import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import Content from 'modules/components/Content';

import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import {DEFAULT_FILTER} from 'modules/constants';
import {
  fetchWorkflowInstanceBySelection,
  fetchWorkflowInstancesCount
} from 'modules/api/instances';

import {
  parseFilterForRequest,
  getFilterQueryString
} from 'modules/utils/filter';

import {getSelectionById} from 'modules/utils/selection';

import Header from '../Header';
import ListView from './ListView';
import Filters from './Filters';
import Selections from './Selections';

import sortArrayByKey from 'modules/utils/sortArrayByKey';

import {
  parseQueryString,
  createNewSelectionFragment,
  getPayload,
  decodeFields
} from './service';
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
    const {
      filterCount,
      selections,
      rollingSelectionIndex,
      instancesInSelectionsCount,
      selectionCount
    } = props.getStateLocally();

    this.state = {
      filter: {},
      filterCount: filterCount || 0,
      openSelection: null,
      selection: createNewSelectionFragment(),
      selections: selections || [],
      instancesInSelectionsCount: instancesInSelectionsCount || 0,
      selectionCount: selectionCount || 0,
      rollingSelectionIndex: rollingSelectionIndex || 0,
      workflow: null,
      activityIds: []
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

  handleStateChange = change => {
    this.setState(change);
  };

  addSelectionToList = selection => {
    const {
      rollingSelectionIndex,
      instancesInSelectionsCount,
      selectionCount,
      selections
    } = this.state;

    const currentSelectionIndex = rollingSelectionIndex;

    // Add Id for each selection
    this.setState(
      prevState => ({
        selections: [
          {
            selectionId: currentSelectionIndex,
            ...selection
          },
          ...prevState.selections
        ],
        rollingSelectionIndex: currentSelectionIndex + 1,
        instancesInSelectionsCount:
          instancesInSelectionsCount + selection.totalCount,
        selectionCount: selectionCount + 1
      }),
      () => {
        this.props.storeStateLocally({
          selectionCount,
          instancesInSelectionsCount,
          selections,
          rollingSelectionIndex
        });
      }
    );
  };

  addNewSelection = async () => {
    const payload = getPayload({state: this.state});
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);
    this.addSelectionToList({...payload, ...instancesDetails});
  };

  addToOpenSelection = async selectionId => {
    const {selections} = this.state;
    const selectiondata = getSelectionById(selections, selectionId);
    const payload = getPayload({state: this.state, selectionId});
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);

    const newSelection = {
      ...selections[selectiondata.index],
      ...payload,
      ...instancesDetails
    };

    selections[selectiondata.index] = newSelection;

    this.setState(selections);
    this.props.storeStateLocally({
      selections
    });
  };

  setFilterFromUrl = () => {
    let {filter} = parseQueryString(this.props.location.search);

    // filter from URL was missing or invalid
    if (!filter) {
      // set default filter selection
      filter = DEFAULT_FILTER;
      this.setFilterInURL(filter);
    }

    this.setState({filter: {...decodeFields(filter)}}, () => {
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

  handleWorkflowChange = workflow => {
    this.setState({workflow});
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  resetFilter = () => {
    this.setFilterInURL(DEFAULT_FILTER);

    // reset filter in local storage
    this.props.storeStateLocally({filter: DEFAULT_FILTER});
  };

  handleFlowNodesDetailsReady = nodes => {
    let activityIds = [];
    let node;
    for (node in nodes) {
      if (nodes[node].type === 'TASK') {
        activityIds.push({
          value: node,
          label: nodes[node].name || 'Unnamed task'
        });
      }
    }

    this.setState({
      activityIds: sortArrayByKey(activityIds, 'label')
    });
  };

  render() {
    const {running, incidents: incidentsCount} = this.props.getStateLocally();
    return (
      <Fragment>
        <Header
          active="instances"
          instances={running}
          filters={this.state.filterCount}
          selections={0} // needs a backend call because selections are complex
          incidents={incidentsCount}
        />
        <Content>
          <Styled.Instances>
            <Styled.Filters>
              <Filters
                filter={this.state.filter}
                activityIds={this.state.activityIds}
                resetFilter={this.resetFilter}
                onFilterChange={this.handleFilterChange}
                onWorkflowVersionChange={this.handleWorkflowChange}
              />
            </Styled.Filters>

            <Styled.Center>
              <SplitPane.Pane isRounded>
                <SplitPane.Pane.Header isRounded>
                  {this.state.workflow
                    ? this.state.workflow.name || this.state.workflow.id
                    : 'Workflow'}
                </SplitPane.Pane.Header>
                <SplitPane.Pane.Body>
                  {this.state.workflow && (
                    <Diagram
                      workflowId={this.state.workflow.id}
                      onFlowNodesDetailsReady={this.handleFlowNodesDetailsReady}
                    />
                  )}
                </SplitPane.Pane.Body>
              </SplitPane.Pane>

              <ListView
                instancesInFilter={this.state.filterCount}
                updateSelection={change => {
                  this.handleStateChange({selection: {...change}});
                }}
                selection={this.state.selection}
                filter={this.state.filter}
                addNewSelection={this.addNewSelection}
                addToOpenSelection={() =>
                  this.addToOpenSelection(this.state.openSelection)
                }
                errorMessage={this.state.errorMessage}
              />
            </Styled.Center>
            <Selections
              openSelection={this.state.openSelection}
              selections={this.state.selections}
              rollingSelectionIndex={this.state.rollingSelectionIndex}
              selectionCount={this.state.selectionCount}
              instancesInSelectionsCount={this.state.instancesInSelectionsCount}
              filter={this.state.filter}
              handleStateChange={this.handleStateChange}
              storeStateLocally={this.props.storeStateLocally}
            />
          </Styled.Instances>
        </Content>
      </Fragment>
    );
  }
}

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
