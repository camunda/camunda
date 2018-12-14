import React, {Component} from 'react';
import PropTypes from 'prop-types';

import {isEqual, isEmpty, sortBy} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  SORT_ORDER,
  DEFAULT_MAX_RESULTS
} from 'modules/constants';

import {
  fetchWorkflowInstances,
  fetchWorkflowInstancesStatistics
} from 'modules/api/instances';

import {
  parseFilterForRequest,
  getFilterWithWorkflowIds
} from 'modules/utils/filter';

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
    groupedWorkflowInstances: PropTypes.object,
    onFilterChange: PropTypes.func.isRequired,
    diagramWorkflow: PropTypes.shape({
      id: PropTypes.string,
      bpmnProcessId: PropTypes.string,
      name: PropTypes.string
    }),
    diagramNodes: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.string,
        name: PropTypes.string
      })
    )
  };

  constructor(props) {
    super(props);
    const {filterCount} = props.getStateLocally();

    this.state = {
      filterCount: filterCount || 0,
      statistics: [],
      workflowInstances: [],
      workflowInstancesLoaded: false,
      firstElement: 0,
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
      this.props.storeStateLocally({filterCount: instances.totalCount});

      // filter has changed but the diagram is the same
      if (
        hasFilterChanged &&
        isEqual(prevProps.diagramWorkflow, this.props.diagramWorkflow)
      ) {
        // refetch statiscs
        this.setState({statistics: []});
        this.fetchDiagramStatistics();
      }
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

  fetchDiagramStatistics = async () => {
    if (isEmpty(this.props.diagramWorkflow)) {
      return;
    }

    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      this.props.filter,
      this.props.groupedWorkflowInstances
    );

    const statistics = await fetchWorkflowInstancesStatistics({
      queries: [parseFilterForRequest(filterWithWorkflowIds)]
    });

    this.setState({statistics});
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
              this.props.filter,
              this.props.groupedWorkflowInstances
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

  handleFilterChange = async newFilter => {
    let hasNewValue = false;

    for (let key in newFilter) {
      if (this.props.filter[key] !== newFilter[key]) {
        hasNewValue = true;
        break;
      }
    }

    // only change the URL if a new value for a field is provided
    if (hasNewValue) {
      // TODO: Once filter gets moved to context, this logic should move to selecitons context.
      // this.resetSelections();
      const filter = {...this.props.filter, ...newFilter};
      this.props.onFilterChange(filter);
    }
  };

  handleFilterReset = () => {
    this.props.onFilterChange(DEFAULT_FILTER);
    // TODO: Once filter gets moved to context, this logic should move to selecitons context.
    // this.resetSelections();
  };

  handleFlowNodeSelection = flowNodeId => {
    this.handleFilterChange({activityId: flowNodeId});
  };

  getFilterQuery = () => {
    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      this.props.filter,
      this.state.groupedWorkflowInstances
    );

    return parseFilterForRequest(filterWithWorkflowIds);
  };

  render() {
    const workflowName = getWorkflowName(
      isEmpty(this.props.diagramWorkflow)
        ? this.props.groupedWorkflowInstances[this.props.filter.workflow]
        : this.props.diagramWorkflow
    );
    const activityIds = getTaskNodes(this.props.diagramNodes).map(item => {
      return {value: item.id, label: item.name};
    });

    return (
      <SelectionProvider getFilterQuery={this.getFilterQuery}>
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
                groupedWorkflowInstances={this.props.groupedWorkflowInstances}
                filter={this.props.filter}
                filterCount={this.state.filterCount}
                onFilterReset={this.handleFilterReset}
                onFilterChange={this.handleFilterChange}
              />
            </Styled.Filters>

            <Styled.Center titles={{top: 'Workflow', bottom: 'Instances'}}>
              <Styled.Pane>
                <Styled.PaneHeader>{workflowName}</Styled.PaneHeader>
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
                  {!isEmpty(this.props.diagramWorkflow) && (
                    <Diagram
                      flowNodesStatisticsOverlay={this.state.statistics}
                      onFlowNodesDetailsReady={this.fetchDiagramStatistics}
                      onFlowNodeSelected={this.handleFlowNodeSelection}
                      selectedFlowNode={this.props.filter.activityId}
                      selectableFlowNodes={activityIds.map(item => item.value)}
                      workflowId={this.props.diagramWorkflow.id}
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
