import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {isEqual, isEmpty} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  SORT_ORDER,
  DEFAULT_MAX_RESULTS,
  DEFAULT_FIRST_ELEMENT,
  PAGE_TITLE
} from 'modules/constants';
import {
  fetchWorkflowInstancesStatistics,
  fetchWorkflowInstances,
  fetchGroupedWorkflows
} from 'modules/api/instances';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
  getFilterQueryString,
  getWorkflowByVersion
} from 'modules/utils/filter';
import {formatGroupedWorkflows} from 'modules/utils/instance';

import Instances from './Instances';
import {parseQueryString, decodeFields, fetchDiagramModel} from './service';
class InstancesContainer extends Component {
  static propTypes = {
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    location: PropTypes.object.isRequired,
    history: PropTypes.object.isRequired
  };

  constructor(props) {
    super(props);

    this.state = {
      diagramModel: {},
      statistics: [],
      filter: {},
      filterCount: 0,
      groupedWorkflows: {},
      workflowInstances: [],
      workflowInstancesLoaded: false,
      firstElement: DEFAULT_FIRST_ELEMENT,
      sorting: DEFAULT_SORTING
    };
  }

  async componentDidMount() {
    document.title = PAGE_TITLE.INSTANCES;

    // fetch groupedWorflows and workflow instances in parallel
    const groupedWorkflows = await fetchGroupedWorkflows();

    this.setState(
      {
        groupedWorkflows: formatGroupedWorkflows(groupedWorkflows)
      },
      () => {
        // only read the url filter once the fetched data is in the state
        const urlFilter = parseQueryString(this.props.location.search).filter;
        this.readUrlFilter(urlFilter);
      }
    );
  }

  async componentDidUpdate(prevProps, prevState) {
    // url filter has changed
    const prevUrlFilter = parseQueryString(prevProps.location.search).filter;
    const currentUrlFilter = parseQueryString(this.props.location.search)
      .filter;
    const hasUrlFilterChanged = !isEqual(prevUrlFilter, currentUrlFilter);

    if (hasUrlFilterChanged) {
      return this.readUrlFilter(currentUrlFilter);
    }

    // if any of these change, re-fetch workflowInstances
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortingChanged = !isEqual(prevState.sorting, this.state.sorting);
    const hasFilterChanged = !isEqual(prevState.filter, this.state.filter);

    if (hasFilterChanged || hasSortingChanged || hasFirstElementChanged) {
      // fetch stats when the state.filter has changed & there is a diagram
      if (this.state.diagramModel.definitions && hasFilterChanged) {
        const statistics = await this.fetchStatistics();
        this.setState({statistics});
      }

      const instances = await this.fetchWorkflowInstances();

      this.setState({
        workflowInstances: instances.workflowInstances,
        filterCount: instances.totalCount,
        workflowInstancesLoaded: true
      });

      // update local storage data
      this.props.storeStateLocally({filterCount: instances.totalCount});
    }
  }

  fetchWorkflowInstances = async () => {
    const {sorting, firstElement} = this.state;
    const instances = await fetchWorkflowInstances({
      queries: [
        parseFilterForRequest(
          decodeFields(
            getFilterWithWorkflowIds(
              this.state.filter,
              this.state.groupedWorkflows
            )
          )
        )
      ],
      sorting,
      firstResult: firstElement,
      maxResults: DEFAULT_MAX_RESULTS
    });

    return instances;
  };

  fetchStatistics = async () => {
    const {filter, groupedWorkflows} = this.state;
    const workflowByVersion = getWorkflowByVersion(
      groupedWorkflows[filter.workflow],
      filter.version
    );

    if (isEmpty(workflowByVersion)) {
      return;
    }

    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      filter,
      groupedWorkflows
    );

    return await fetchWorkflowInstancesStatistics({
      queries: [parseFilterForRequest(decodeFields(filterWithWorkflowIds))]
    });
  };

  /**
   * Reads the current filter in url and either:
   * 1- fixes it in the url if it's not valid
   * 2- updates the state accordingly if it's valid
   */
  readUrlFilter = async urlFilter => {
    // updates the local storage with the curent state.filter
    const updateLocalStorageFilter = () => {
      this.props.storeStateLocally({filter: this.state.filter});
    };

    const {isValid, update} = await this.validateUrlFilter(urlFilter);

    if (isValid) {
      return this.setState(update, updateLocalStorageFilter);
    }

    this.setFilterInURL(update.urlFilter);
    updateLocalStorageFilter();
  };

  /**
   * Parses the url filter and returns an update object containing the sanitized filter.
   * If the url filter is valid, the update object can be used to cleanup the state.
   * If the url filter is not valid, the update can be used to cleanup url filter.
   * @returns {isValid, update}
   */
  validateUrlFilter = async filter => {
    // (1) empty filter
    if (!filter) {
      return {isValid: false, update: {urlFilter: DEFAULT_FILTER}};
    }

    let {workflow, version, activityId, ...otherFilters} = filter;

    const sorting = this.shouldResetSorting({filter})
      ? DEFAULT_SORTING
      : this.state.sorting;

    // (2):
    // - if there is no workflow or version and there is an activityId, clear filter from workflow, version & activityId
    // - if there is no workflow or version and there is no activityId, reset diagramModel and statistics
    if (!workflow || !version) {
      return activityId
        ? {isValid: false, update: {urlFilter: otherFilters}}
        : {
            isValid: true,
            update: {
              filter,
              diagramModel: {},
              statistics: [],
              firstElement: DEFAULT_FIRST_ELEMENT,
              sorting
            }
          };
    }

    // (3) validate workflow
    const isWorkflowValid = Boolean(this.state.groupedWorkflows[workflow]);

    // (3) if the workflow is invalid, remove it from the url filter
    if (!isWorkflowValid) {
      return {isValid: false, update: {urlFilter: otherFilters}};
    }

    // (4):
    // - if the version is 'all' and there is an activityId, remove the activityId from the url filter
    // - if the version is 'all' and there is no activityId, reset diagramModel & statistics
    if (version === 'all') {
      return activityId
        ? {
            isValid: false,
            update: {urlFilter: {...otherFilters, workflow, version}}
          }
        : {
            isValid: true,
            update: {
              filter,
              diagramModel: {},
              statistics: [],
              firstElement: DEFAULT_FIRST_ELEMENT,
              sorting
            }
          };
    }

    // check workflow & version combination
    const workflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflows[workflow],
      version
    );

    // (5) if version is invalid, remove workflow from the url filter
    if (!Boolean(workflowByVersion)) {
      return {isValid: false, update: {urlFilter: otherFilters}};
    }

    const currentWorkflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflows[this.state.filter.workflow],
      this.state.filter.version
    );

    const hasWorkflowChanged = !isEqual(
      workflowByVersion,
      currentWorkflowByVersion
    );

    let {diagramModel} = this.state;

    if (hasWorkflowChanged) {
      diagramModel = await fetchDiagramModel(workflowByVersion.id);
    }

    // (6) if activityId is invalid, remove it from the url filter
    if (activityId && !diagramModel.bpmnElements[activityId]) {
      return {
        isValid: false,
        update: {urlFilter: {...otherFilters, workflow, version}}
      };
    }

    // (7) if the workflow didn't change, we can immediatly update the state
    if (!hasWorkflowChanged) {
      return {
        isValid: true,
        update: {
          filter,
          firstElement: DEFAULT_FIRST_ELEMENT,
          sorting
        }
      };
    }

    // (8) Set new data in state and clear current statistics
    return {
      isValid: true,
      update: {
        filter,
        diagramModel,
        statistics: [],
        firstElement: DEFAULT_FIRST_ELEMENT,
        sorting
      }
    };
  };

  /**
   * Helper function to determine based on filter & sorting if state.sorting should
   * be reset to its default value
   * e.g. when filter is only running and sorting is by end date
   * @returns {bool}
   * @param filter, default: state.filter
   * @param sorting, default: state.sorting
   */
  shouldResetSorting = ({
    filter = this.state.filter,
    sorting = this.state.sorting
  }) => {
    const isFinishedInFilter = filter.canceled || filter.completed;

    // reset sorting  by endDate when no finished filter is selected
    return !isFinishedInFilter && sorting.sortBy === 'endDate';
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  handleFilterChange = newFilter => {
    if (!isEqual(newFilter, this.state.filter)) {
      // only change the URL if a new value for a field is provided
      this.setFilterInURL({...this.state.filter, ...newFilter});
    }
  };

  handleFilterReset = () => {
    this.setFilterInURL(DEFAULT_FILTER);
  };

  handleSortingChange = key => {
    const currentSorting = this.state.sorting;

    let newSorting = {sortBy: key, sortOrder: SORT_ORDER.DESC};

    if (
      currentSorting.sortBy === key &&
      currentSorting.sortOrder === SORT_ORDER.DESC
    ) {
      newSorting.sortOrder = SORT_ORDER.ASC;
    }

    // check if sorting needs to be reset
    if (this.shouldResetSorting({sorting: newSorting})) {
      return this.setState({sorting: DEFAULT_SORTING});
    }

    return this.setState({sorting: newSorting});
  };

  handleFirstElementChange = firstElement => this.setState({firstElement});

  render() {
    return (
      <Instances
        {...this.state}
        filter={decodeFields(this.state.filter)}
        onFilterChange={this.handleFilterChange}
        onFilterReset={this.handleFilterReset}
        onFirstElementChange={this.handleFirstElementChange}
        onSort={this.handleSortingChange}
      />
    );
  }
}

const WrappedInstancesContainer = withSharedState(InstancesContainer);
WrappedInstancesContainer.WrappedComponent = InstancesContainer;

export default WrappedInstancesContainer;
