import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import {fetchWorkflowInstanceBySelection} from 'modules/api/instances';

import Content from 'modules/components/Content';
import Panel from 'modules/components/Panel';
import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import {DIRECTION, DEFAULT_FILTER} from 'modules/constants';
import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {
  parseFilterForRequest,
  getFilterQueryString
} from 'modules/utils/filter';

import {BADGE_TYPE} from 'modules/constants';

import Header from '../Header';
import ListView from './ListView';
import SelectionList from './SelectionList';
import {
  parseQueryString,
  createNewSelectionFragment,
  getParentFilter,
  getSelectionById
} from './service';
import Filters from './Filters';
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

  addSelectionToList = async selection => {
    const {
      rollingSelectionIndex: currentSelectionIndex,
      instancesInSelectionsCount,
      selectionCount
    } = this.state;

    // Add Id for each selection
    await this.setState(prevState => ({
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
    }));
    this.props.storeStateLocally({
      selectionCount: this.state.selectionCount,
      instancesInSelectionsCount: this.state.instancesInSelectionsCount,
      selections: this.state.selections,
      rollingSelectionIndex: this.state.rollingSelectionIndex
    });
  };

  addNewSelection = async () => {
    const {selection, filter} = this.state;

    //Replace Sets with arrays.
    const payload = {
      queries: [
        {
          ...filter,
          ...getParentFilter(filter),
          ...selection,
          ids: [...(selection.ids || [])],
          excludeIds: [...(selection.excludeIds || [])]
        }
      ]
    };

    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);

    this.addSelectionToList({
      ...payload,
      ...instancesDetails
    });
  };

  updateSelectionData = async selectionId => {
    const {selection, selections, filter} = this.state;
    const selectiondata = getSelectionById(selections, selectionId);

    const payload = {
      queries: [
        {
          ...filter,
          ...getParentFilter(filter),
          ...selection,
          ids: [...(selection.ids || [])],
          excludeIds: [...(selection.excludeIds || [])]
        },
        ...(selections[selectiondata.index].queries || '')
      ]
    };

    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);

    return {
      ...(selectiondata && selections[selectiondata.index]),
      ...payload,
      ...instancesDetails
    };
  };

  addToCurrentSelection = async selectionId => {
    const selectiondata = getSelectionById(this.state.selections, selectionId);
    const newSelection = await this.updateSelectionData(selectionId);

    const {selections} = this.state;

    selections[selectiondata.index] = newSelection;

    this.setState(selections);
    this.props.storeStateLocally({
      selections
    });
  };

  toggleSelection = selectionId => {
    this.setState({
      openSelection:
        selectionId !== this.state.openSelection ? selectionId : null
    });
  };

  updateSelection = change => {
    this.setState({
      selection: {...change}
    });
  };

  deleteSelection = async deleteId => {
    const {selections, instancesInSelectionsCount, selectionCount} = this.state;

    const selectionToRemove = getSelectionById(selections, deleteId);

    // remove the selection
    selections.splice(selectionToRemove.index, 1);

    await this.setState({
      selections,
      instancesInSelectionsCount:
        instancesInSelectionsCount - selectionToRemove.totalCount,
      selectionCount: selectionCount - 1 || 0
    });
    this.props.storeStateLocally({
      selections,
      instancesInSelectionsCount: this.state.instancesInSelectionsCount,
      selectionCount: this.state.selectionCount
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
    const activityIds = [];
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
      activityIds
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
                onSelectionUpdate={change => {
                  this.updateSelection(change);
                }}
                selection={this.state.selection}
                filter={this.state.filter}
                addNewSelection={this.addNewSelection}
                addToCurrentSelection={() =>
                  this.addToCurrentSelection(this.state.openSelection)
                }
                errorMessage={this.state.errorMessage}
              />
            </Styled.Center>
            <Styled.Selections>
              <Panel isRounded>
                <Styled.SelectionHeader isRounded>
                  <span>Selections</span>
                  <Styled.Badge
                    type={BADGE_TYPE.COMBOSELECTION}
                    badgeContent={this.state.instancesInSelectionsCount}
                    circleContent={this.state.selectionCount}
                  />
                </Styled.SelectionHeader>
                <Panel.Body>
                  <SelectionList
                    openSelection={this.state.openSelection}
                    selections={this.state.selections}
                    onDelete={this.deleteSelection}
                    onToggle={this.toggleSelection}
                  />
                </Panel.Body>
                <Styled.RightExpandButton
                  direction={DIRECTION.RIGHT}
                  isExpanded={true}
                />
                <Panel.Footer />
              </Panel>
            </Styled.Selections>
          </Styled.Instances>
        </Content>
      </Fragment>
    );
  }
}

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
