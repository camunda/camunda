import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import {isEqual, sortBy, isEmpty} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import {DEFAULT_FILTER, PAGE_TITLE} from 'modules/constants';
import {
  fetchWorkflowInstanceBySelection,
  fetchWorkflowInstancesCount,
  fetchGroupedWorkflowInstances,
  fetchWorkflowInstancesStatistics
} from 'modules/api/instances';

import {fetchWorkflowXML} from 'modules/api/diagram';
import {
  parseFilterForRequest,
  getFilterQueryString,
  getFilterWithWorkflowIds,
  getWorkflowByVersion
} from 'modules/utils/filter';

import {getNodesFromXML} from 'modules/utils/bpmn';

import {getSelectionById} from 'modules/utils/selection';

import Header from '../Header';
import ListView from './ListView';
import Filters from './Filters';
import Selections from './Selections';

import {
  parseQueryString,
  getPayload,
  decodeFields,
  getEmptyDiagramMessage,
  getActivityIds
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
      instancesInSelectionsCount,
      rollingSelectionIndex,
      selectionCount,
      selections
    } = props.getStateLocally();

    this.state = {
      activityIds: [],
      filter: {},
      filterCount: filterCount || 0,
      instancesInSelectionsCount: instancesInSelectionsCount || 0,
      openSelection: null,
      rollingSelectionIndex: rollingSelectionIndex || 0,
      selection: {all: false, ids: [], excludeIds: []},
      selectionCount: selectionCount || 0,
      selections: selections || [],
      diagramWorkflow: {},
      groupedWorkflowInstances: {},
      statistics: []
    };
  }

  async componentDidMount() {
    document.title = PAGE_TITLE.INSTANCES;
    const groupedWorkflows = await fetchGroupedWorkflowInstances();
    this.setGroupedWorkflowInstances(groupedWorkflows);

    await this.validateAndSetUrlFilter();
    await this.updateFilterRelatedState();
    this.updateLocalStorageFilter();
  }

  async componentDidUpdate(prevProps, prevState) {
    // filter in url has changed
    if (prevProps.location.search !== this.props.location.search) {
      await this.validateAndSetUrlFilter();
      await this.updateFilterRelatedState();
      this.updateLocalStorageFilter();
    }
  }

  updateLocalStorageFilter = () => {
    // write current filter selection to local storage
    this.props.storeStateLocally({filter: this.state.filter});
  };

  fetchDiagramStatistics = async () => {
    let filter = Object.assign({}, this.state.filter);

    if (isEmpty(this.state.diagramWorkflow)) {
      return;
    }

    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      filter,
      this.state.groupedWorkflowInstances
    );

    const statistics = await fetchWorkflowInstancesStatistics({
      queries: [parseFilterForRequest(filterWithWorkflowIds)]
    });

    this.setState({statistics});
  };

  updateFilterRelatedState = async () => {
    const {filter} = parseQueryString(this.props.location.search);
    const {workflow, version} = filter;
    // the url filter has fields that result in showing a diagram
    const hasWorflowDiagramData =
      Boolean(workflow) && Boolean(version) && version !== 'all';
    const workflowDiagramNodes = hasWorflowDiagramData
      ? await this.fetchDiagramNodes(
          getFilterWithWorkflowIds(filter, this.state.groupedWorkflowInstances)
            .workflowIds[0]
        )
      : [];

    // fetch new statistics only if filter changes for the same diagram
    // if the filter.workflow && filter.version change, statistics are fetched onFlowNodesDetailsReady
    const shouldRefreshStatistics =
      this.state.filter.workflow === filter.workflow &&
      this.state.filter.version === filter.version &&
      !isEqual(this.state.filter, filter);

    const newState = {
      filter: {...decodeFields(filter)},
      diagramWorkflow: !hasWorflowDiagramData
        ? {}
        : getWorkflowByVersion(
            this.state.groupedWorkflowInstances[workflow],
            version
          ),
      activityIds: !hasWorflowDiagramData
        ? []
        : sortBy(getActivityIds(workflowDiagramNodes), item =>
            item.label.toLowerCase()
          )
    };

    // reset statistics to prevent sending outdated statistics to Diagram
    if (!shouldRefreshStatistics) {
      newState.statistics = [];
    }

    this.setState({...newState}, async () => {
      if (shouldRefreshStatistics) {
        await this.fetchDiagramStatistics();
      }

      this.handleFilterCount();
    });
  };

  validateAndSetUrlFilter = async () => {
    const {filter} = parseQueryString(this.props.location.search);
    const validFilter = await this.getValidFilter(filter);

    // update URL with new valid filter
    if (!isEqual(filter, validFilter)) {
      this.setFilterInURL(validFilter);
    }
  };

  getValidFilter = async filter => {
    if (!filter) {
      return DEFAULT_FILTER;
    }

    let {workflow, version, activityId, ...otherFilters} = filter;

    // stop validation
    if (!workflow || (workflow && !version)) {
      return otherFilters;
    }

    // validate workflow
    const isWorkflowValid = Boolean(
      this.state.groupedWorkflowInstances[workflow]
    );

    if (!isWorkflowValid) {
      return otherFilters;
    }

    if (version === 'all') {
      return {...otherFilters, workflow, version};
    }

    // check workflow & version combination
    const workflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflowInstances[workflow],
      version
    );

    // version is not valid for workflow
    if (!Boolean(workflowByVersion)) {
      return otherFilters;
    }

    // check activityID
    if (!activityId) {
      return {...otherFilters, workflow, version};
    } else {
      const nodes = await this.fetchDiagramNodes(workflowByVersion.id);
      const isActivityIdValid = Boolean(nodes[activityId]);
      return isActivityIdValid
        ? {...otherFilters, workflow, version, activityId}
        : {...otherFilters, workflow, version};
    }
  };

  fetchDiagramNodes = async workflowId => {
    const xml = await fetchWorkflowXML(workflowId);
    const nodes = await getNodesFromXML(xml);

    return nodes;
  };

  setGroupedWorkflowInstances = workflows => {
    const groupedWorkflowInstances = workflows.reduce((obj, value) => {
      obj[value.bpmnProcessId] = {
        ...value
      };

      return obj;
    }, {});

    this.setState({groupedWorkflowInstances});
  };

  handleStateChange = change => {
    this.setState(change);
  };

  addSelectionToList = selection => {
    const {
      rollingSelectionIndex,
      instancesInSelectionsCount,
      selectionCount
    } = this.state;

    const currentSelectionIndex = rollingSelectionIndex + 1;

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
        rollingSelectionIndex: currentSelectionIndex,
        instancesInSelectionsCount:
          instancesInSelectionsCount + selection.totalCount,
        selectionCount: selectionCount + 1,
        openSelection: currentSelectionIndex,
        selection: {all: false, ids: [], excludeIds: []}
      }),
      () => {
        const {
          selections,
          rollingSelectionIndex,
          instancesInSelectionsCount,
          selectionCount
        } = this.state;

        this.props.storeStateLocally({
          selections,
          rollingSelectionIndex,
          instancesInSelectionsCount,
          selectionCount
        });
      }
    );
  };

  handleAddNewSelection = async () => {
    const payload = getPayload({state: this.state});
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);
    this.addSelectionToList({...payload, ...instancesDetails});
  };

  handleAddToSelectionById = async selectionId => {
    const {selections, instancesInSelectionsCount} = this.state;
    const selectiondata = getSelectionById(selections, selectionId);
    const payload = getPayload({state: this.state, selectionId});
    const previousTotalCount = selections[selectiondata.index].totalCount;

    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);

    const newSelection = {
      ...selections[selectiondata.index],
      ...payload,
      ...instancesDetails
    };

    selections[selectiondata.index] = newSelection;

    this.setState(
      {
        selections,
        instancesInSelectionsCount:
          instancesInSelectionsCount -
          previousTotalCount +
          newSelection.totalCount,
        selection: {all: false, ids: [], excludeIds: []},
        openSelection: selectionId
      },
      () => {
        const {instancesInSelectionsCount, selections} = this.state;

        this.props.storeStateLocally({
          instancesInSelectionsCount,
          selections
        });
      }
    );
  };

  handleFilterCount = async () => {
    const filterCount = await fetchWorkflowInstancesCount(
      parseFilterForRequest(
        getFilterWithWorkflowIds(
          this.state.filter,
          this.state.groupedWorkflowInstances
        )
      )
    );

    this.setState({
      filterCount
    });

    // save filterCount to localStorage
    this.props.storeStateLocally({filterCount});
  };

  handleFilterChange = async newFilter => {
    let hasNewValue = false;

    for (let key in newFilter) {
      if (this.state.filter[key] !== newFilter[key]) {
        hasNewValue = true;
        break;
      }
    }

    // only change the URL if a new value for a field is provided
    if (hasNewValue) {
      const filter = {...this.state.filter, ...newFilter};
      this.setFilterInURL(filter);
      this.resetSelections();
    }
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  handleFilterReset = () => {
    this.setFilterInURL(DEFAULT_FILTER);

    // reset diagram
    this.setState({
      diagramWorkflow: {}
    });

    // reset filter in local storage
    this.props.storeStateLocally({filter: DEFAULT_FILTER});

    this.resetSelections();
  };

  resetSelections = () => {
    this.setState({selection: {all: false, ids: [], excludeIds: []}});
  };

  handleFlowNodeSelection = flowNodeId => {
    this.handleFilterChange({activityId: flowNodeId});
  };

  render() {
    const workflowName = !isEmpty(this.state.diagramWorkflow)
      ? this.state.diagramWorkflow.name ||
        this.state.diagramWorkflow.bpmnProcessId
      : 'Workflow';

    return (
      <Fragment>
        <Header
          active="instances"
          filter={this.state.filter}
          filterCount={this.state.filterCount}
          instancesInSelectionsCount={this.state.instancesInSelectionsCount}
          selectionCount={this.state.selectionCount}
        />
        <Styled.Instances>
          <Styled.Content>
            <Styled.Filters>
              <Filters
                filter={this.state.filter}
                filterCount={this.state.filterCount}
                activityIds={this.state.activityIds}
                onFilterReset={this.handleFilterReset}
                onFilterChange={this.handleFilterChange}
                groupedWorkflowInstances={this.state.groupedWorkflowInstances}
              />
            </Styled.Filters>

            <Styled.Center titles={{top: 'Workflow', bottom: 'Instances'}}>
              <Styled.Pane>
                <Styled.PaneHeader>{workflowName}</Styled.PaneHeader>
                <SplitPane.Pane.Body>
                  {!this.state.filter.workflow && (
                    <Styled.EmptyMessageWrapper>
                      <Styled.DiagramEmptyMessage
                        message={`There is no Workflow selected.\n
To see a diagram, select a Workflow in the Filters panel.`}
                        data-test="data-test-noWorkflowMessage"
                      />
                    </Styled.EmptyMessageWrapper>
                  )}
                  {this.state.filter.version === 'all' && (
                    <Styled.EmptyMessageWrapper>
                      <Styled.DiagramEmptyMessage
                        message={getEmptyDiagramMessage(workflowName)}
                        data-test="data-test-allVersionsMessage"
                      />
                    </Styled.EmptyMessageWrapper>
                  )}
                  {!isEmpty(this.state.diagramWorkflow) && (
                    <Diagram
                      workflowId={this.state.diagramWorkflow.id}
                      onFlowNodesDetailsReady={this.fetchDiagramStatistics}
                      flowNodesStatisticsOverlay={this.state.statistics}
                      selectedFlowNode={this.state.filter.activityId}
                      onFlowNodeSelected={this.handleFlowNodeSelection}
                      selectableFlowNodes={this.state.activityIds.map(
                        item => item.value
                      )}
                    />
                  )}
                </SplitPane.Pane.Body>
              </Styled.Pane>

              <ListView
                openSelection={this.state.openSelection}
                filterCount={this.state.filterCount}
                onUpdateSelection={change => {
                  this.handleStateChange({selection: {...change}});
                }}
                selection={this.state.selection}
                selections={this.state.selections}
                filter={getFilterWithWorkflowIds(
                  this.state.filter,
                  this.state.groupedWorkflowInstances
                )}
                errorMessage={this.state.errorMessage}
                onAddNewSelection={this.handleAddNewSelection}
                onAddToSpecificSelection={this.handleAddToSelectionById}
                onAddToOpenSelection={() =>
                  this.handleAddToSelectionById(this.state.openSelection)
                }
              />
            </Styled.Center>
          </Styled.Content>
          <Selections
            openSelection={this.state.openSelection}
            selections={this.state.selections}
            rollingSelectionIndex={this.state.rollingSelectionIndex}
            selectionCount={this.state.selectionCount}
            instancesInSelectionsCount={this.state.instancesInSelectionsCount}
            filter={getFilterWithWorkflowIds(
              this.state.filter,
              this.state.groupedWorkflowInstances
            )}
            storeStateLocally={this.props.storeStateLocally}
            onStateChange={this.handleStateChange}
          />
        </Styled.Instances>
      </Fragment>
    );
  }
}

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
