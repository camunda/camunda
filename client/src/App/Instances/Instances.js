import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {isEqual, isEmpty, sortBy} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
  getWorkflowByVersion
} from 'modules/utils/filter';

import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  SORT_ORDER,
  DEFAULT_MAX_RESULTS,
  DEFAULT_FIRST_ELEMENT
} from 'modules/constants';

import {fetchWorkflowInstances} from 'modules/api/instances';

import {SelectionProvider} from 'modules/contexts/SelectionContext';

import Header from '../Header';
import ListView from './ListView';
import Filters from './Filters';
import Selections from './Selections';

import {getEmptyDiagramMessage, getWorkflowName, getTaskNodes} from './service';
import * as Styled from './styled.js';

class Instances extends Component {
  static propTypes = {
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    filter: PropTypes.shape({
      workflow: PropTypes.string,
      version: PropTypes.string,
      active: PropTypes.bool,
      ids: PropTypes.string,
      startDate: PropTypes.string,
      endDate: PropTypes.string,
      errorMessage: PropTypes.string,
      incidents: PropTypes.bool,
      canceled: PropTypes.bool,
      completed: PropTypes.bool,
      activityId: PropTypes.string
    }),
    groupedWorkflows: PropTypes.object,
    onFilterChange: PropTypes.func.isRequired,
    diagramModel: PropTypes.shape({
      bpmnElements: PropTypes.object,
      definitions: PropTypes.object
    }).isRequired,
    statistics: PropTypes.array.isRequired
  };

  constructor(props) {
    super(props);
    this.state = {
      filterCount: 0,
      workflowInstances: [],
      workflowInstancesLoaded: false,
      firstElement: DEFAULT_FIRST_ELEMENT,
      sorting: DEFAULT_SORTING
    };
  }

  async componentDidMount() {
    const instances = await this.fetchWorkflowInstances(
      this.state.sorting,
      this.state.firstElement
    );

    this.props.storeStateLocally({filterCount: instances.totalCount});

    this.setState({
      workflowInstancesLoaded: true,
      workflowInstances: instances.workflowInstances,
      filterCount: instances.totalCount
    });
  }

  async componentDidUpdate(prevProps, prevState) {
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortingChanged = !isEqual(prevState.sorting, this.state.sorting);
    const hasFilterChanged = !isEqual(prevProps.filter, this.props.filter);

    if (hasFilterChanged || hasSortingChanged || hasFirstElementChanged) {
      const isFinishedInFilter =
        this.props.filter.canceled || this.props.filter.completed;

      // reset sorting  by endDate when no finished filter is selected
      if (!isFinishedInFilter && this.state.sorting.sortBy === 'endDate') {
        return this.setState({sorting: DEFAULT_SORTING});
      }

      // reset firstElement when filter changes
      if (
        hasFilterChanged &&
        this.state.firstElement !== DEFAULT_FIRST_ELEMENT
      ) {
        return this.setState({firstElement: DEFAULT_FIRST_ELEMENT});
      }

      const instances = await this.fetchWorkflowInstances(
        this.state.sorting,
        this.state.firstElement
      );

      this.setState({
        workflowInstancesLoaded: true,
        workflowInstances: instances.workflowInstances,
        filterCount: instances.totalCount
      });

      // update local storage data
      this.props.storeStateLocally({filterCount: instances.totalCount});
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

  fetchWorkflowInstances = async (
    sorting = DEFAULT_SORTING,
    firstElement = DEFAULT_FIRST_ELEMENT
  ) => {
    const instances = await fetchWorkflowInstances({
      queries: [
        {
          ...parseFilterForRequest(
            getFilterWithWorkflowIds(
              this.props.filter,
              this.props.groupedWorkflows
            )
          )
        }
      ],
      sorting,
      firstResult: firstElement,
      maxResults: DEFAULT_MAX_RESULTS
    });

    return instances;
  };

  handleFilterReset = () => {
    this.props.onFilterChange(DEFAULT_FILTER);
  };

  handleFlowNodeSelection = flowNodeId => {
    this.props.onFilterChange({activityId: flowNodeId});
  };

  getFilterQuery = () => {
    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      this.props.filter,
      this.props.groupedWorkflows
    );

    return parseFilterForRequest(filterWithWorkflowIds);
  };

  getCurrentWorkflowByVersion = () => {
    const {
      filter: {workflow, version},
      groupedWorkflows
    } = this.props;
    return getWorkflowByVersion(groupedWorkflows[workflow], version);
  };

  getCurrentWorkflowName = () => {
    const currentWorkflowByVersion = this.getCurrentWorkflowByVersion();

    if (!isEmpty(currentWorkflowByVersion)) {
      return getWorkflowName(currentWorkflowByVersion);
    }

    const {workflow} = this.props.filter;
    const currentWorkflow = this.props.groupedWorkflows[workflow];
    return getWorkflowName(currentWorkflow);
  };

  render() {
    const currentWorkflowByVersion = this.getCurrentWorkflowByVersion();
    const workflowName = this.getCurrentWorkflowName();
    const activityIds = getTaskNodes(this.props.diagramModel.bpmnElements).map(
      item => {
        return {value: item.id, label: item.name};
      }
    );

    return (
      <SelectionProvider
        getFilterQuery={this.getFilterQuery}
        filter={this.props.filter}
      >
        <Header
          active="instances"
          filter={this.props.filter}
          filterCount={this.state.filterCount}
        />
        <Styled.Instances>
          <VisuallyHiddenH1>Camunda Operate Instances</VisuallyHiddenH1>
          <Styled.Content>
            <Styled.Filters>
              <Filters
                activityIds={sortBy(activityIds, item =>
                  item.label.toLowerCase()
                )}
                groupedWorkflows={this.props.groupedWorkflows}
                filter={this.props.filter}
                filterCount={this.state.filterCount}
                onFilterReset={this.handleFilterReset}
                onFilterChange={this.props.onFilterChange}
              />
            </Styled.Filters>

            <Styled.Center titles={{top: 'Workflow', bottom: 'Instances'}}>
              <Styled.Pane>
                <Styled.PaneHeader>
                  <span data-test="instances-diagram-title">
                    {workflowName}
                  </span>
                </Styled.PaneHeader>
                <SplitPane.Pane.Body>
                  {!this.props.filter.workflow && (
                    <Styled.EmptyMessageWrapper>
                      <Styled.DiagramEmptyMessage
                        message={`There is no Workflow selected.\n
To see a diagram, select a Workflow in the Filters panel.`}
                        data-test="data-test-noWorkflowMessage"
                      />
                    </Styled.EmptyMessageWrapper>
                  )}
                  {this.props.filter.version === 'all' && (
                    <Styled.EmptyMessageWrapper>
                      <Styled.DiagramEmptyMessage
                        message={getEmptyDiagramMessage(workflowName)}
                        data-test="data-test-allVersionsMessage"
                      />
                    </Styled.EmptyMessageWrapper>
                  )}
                  {!isEmpty(currentWorkflowByVersion) &&
                    this.props.diagramModel.definitions && (
                      <Diagram
                        flowNodesStatistics={this.props.statistics}
                        onFlowNodeSelected={this.handleFlowNodeSelection}
                        selectedFlowNode={this.props.filter.activityId}
                        selectableFlowNodes={activityIds.map(
                          item => item.value
                        )}
                        definitions={this.props.diagramModel.definitions}
                      />
                    )}
                </SplitPane.Pane.Body>
              </Styled.Pane>

              <ListView
                instances={this.state.workflowInstances}
                instancesLoaded={this.state.workflowInstancesLoaded}
                fetchWorkflowInstances={this.fetchWorkflowInstances}
                filter={this.props.filter}
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
