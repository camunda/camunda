import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {isEqual, sortBy, isEmpty} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {
  DEFAULT_FILTER,
  PAGE_TITLE,
  DEFAULT_SORTING,
  SORT_ORDER
} from 'modules/constants';

import {
  fetchWorkflowInstances,
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
import {SelectionProvider} from 'modules/contexts/SelectionContext';

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
    const {filterCount} = props.getStateLocally();

    this.state = {
      activityIds: [],
      filter: {},
      filterCount: filterCount || 0,
      diagramWorkflow: {},
      groupedWorkflowInstances: {},
      statistics: [],
      workflowInstances: [],
      workflowInstancesLoaded: false,
      firstElement: 0,
      sorting: DEFAULT_SORTING
    };
  }

  async componentDidMount() {
    document.title = PAGE_TITLE.INSTANCES;
    const groupedWorkflows = await fetchGroupedWorkflowInstances();
    this.setGroupedWorkflowInstances(groupedWorkflows);
    await this.validateAndSetUrlFilter();
    await this.setFilterAndRelatedState();
    this.updateLocalStorageFilter();
  }

  async componentDidUpdate(prevProps, prevState) {
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortingChanged = !isEqual(prevState.sorting, this.state.sorting);
    const hasFilterChanged = !isEqual(prevState.filter, this.state.filter);

    if (hasFilterChanged || hasSortingChanged || hasFirstElementChanged) {
      const isFinishedInFilter =
        this.state.filter.canceled || this.state.filter.completed;

      // reset sorting  by endDate when no finished filter is selected
      if (!isFinishedInFilter && this.state.sorting.sortBy === 'endDate') {
        return this.setState({sorting: DEFAULT_SORTING});
      }

      const instances = await this.fetchWorkflowInstances(
        this.state.sorting,
        this.state.firstElement
      );

      this.setState({
        workflowInstancesLoaded: true,
        workflowInstances: instances.workflowInstances,
        filterCount: instances.totalCount,
        firstElement: hasFilterChanged ? 0 : this.state.firstElement
      });

      // update local storage data
      hasFilterChanged && this.updateLocalStorageFilter();
      this.props.storeStateLocally({filterCount: instances.totalCount});
    }

    const hasFilterUrlChanged = !isEqual(
      parseQueryString(prevProps.location.search).filter,
      parseQueryString(this.props.location.search).filter
    );

    if (hasFilterUrlChanged) {
      await this.validateAndSetUrlFilter();
      await this.setFilterAndRelatedState();
    }
  }

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

  handleFirstElementChange = firstElement => this.setState({firstElement});

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

  setFilterAndRelatedState = async () => {
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
      hasWorflowDiagramData &&
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

  fetchWorkflowInstances = async (
    sorting = DEFAULT_SORTING,
    firstElement = 0
  ) => {
    const instances = await fetchWorkflowInstances({
      queries: [
        {
          ...parseFilterForRequest(
            getFilterWithWorkflowIds(
              this.state.filter,
              this.state.groupedWorkflowInstances
            )
          )
        }
      ],
      sorting,
      firstResult: firstElement,
      maxResults: 50
    });

    return instances;
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
      // TODO: Once filter gets moved to context, this logic should move to selecitons context.
      // this.resetSelections();
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
    // TODO: Once filter gets moved to context, this logic should move to selecitons context.
    // this.resetSelections();
  };

  handleFlowNodeSelection = flowNodeId => {
    this.handleFilterChange({activityId: flowNodeId});
  };

  getSelectionPayload = ({selectionState, selectionId}) => {
    return getPayload({state: {...this.state, ...selectionState}, selectionId});
  };

  render() {
    const workflowName = !isEmpty(this.state.diagramWorkflow)
      ? this.state.diagramWorkflow.name ||
        this.state.diagramWorkflow.bpmnProcessId
      : 'Workflow';

    return (
      <SelectionProvider getSelectionPayload={this.getSelectionPayload}>
        <Header
          active="instances"
          filter={this.state.filter}
          filterCount={this.state.filterCount}
        />
        <Styled.Instances>
          <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
          <Styled.Content>
            <Styled.Filters>
              <Filters
                activityIds={this.state.activityIds}
                groupedWorkflowInstances={this.state.groupedWorkflowInstances}
                filter={this.state.filter}
                filterCount={this.state.filterCount}
                onFilterReset={this.handleFilterReset}
                onFilterChange={this.handleFilterChange}
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
                      flowNodesStatisticsOverlay={this.state.statistics}
                      onFlowNodesDetailsReady={this.fetchDiagramStatistics}
                      onFlowNodeSelected={this.handleFlowNodeSelection}
                      selectedFlowNode={this.state.filter.activityId}
                      selectableFlowNodes={this.state.activityIds.map(
                        item => item.value
                      )}
                      workflowId={this.state.diagramWorkflow.id}
                    />
                  )}
                </SplitPane.Pane.Body>
              </Styled.Pane>

              <ListView
                instances={this.state.workflowInstances}
                instancesLoaded={this.state.workflowInstancesLoaded}
                fetchWorkflowInstances={this.fetchWorkflowInstances}
                filter={getFilterWithWorkflowIds(
                  this.state.filter,
                  this.state.groupedWorkflowInstances
                )}
                filterCount={this.state.filterCount}
                onSort={this.handleSorting}
                sorting={this.state.sorting}
                firstElement={this.state.firstElement}
                onFirstElementChange={this.handleFirstElementChange}
              />
            </Styled.Center>
          </Styled.Content>
          <Selections />
        </Styled.Instances>
      </SelectionProvider>
    );
  }
}

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
