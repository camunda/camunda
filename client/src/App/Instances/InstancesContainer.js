/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {isEqual, isEmpty} from 'lodash';

import {withData} from 'modules/DataManager';
import withSharedState from 'modules/components/withSharedState';
import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  SORT_ORDER,
  DEFAULT_MAX_RESULTS,
  DEFAULT_FIRST_ELEMENT,
  PAGE_TITLE,
  DEFAULT_FILTER_CONTROLLED_VALUES
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

import {sanitizeFilter} from './Filters/service';

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
        this.setFilterFromUrl();
      }
    );
  }

  async componentDidUpdate(prevProps, prevState) {
    if (
      prevProps.location.search !== this.props.location.search &&
      !isEmpty(this.state.groupedWorkflows)
    ) {
      this.setFilterFromUrl();
    }
    // if any of these change, re-fetch workflowInstances
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortingChanged = !isEqual(prevState.sorting, this.state.sorting);
    const hasFilterChanged = !isEqual(prevState.filter, this.state.filter);

    if (hasFilterChanged || hasSortingChanged || hasFirstElementChanged) {
      this.setState({workflowInstancesLoaded: false});
      const instances = await this.fetchWorkflowInstances();

      this.setState({
        workflowInstances: instances.workflowInstances,
        filterCount: instances.totalCount,
        workflowInstancesLoaded: true
      });

      if (!hasFilterChanged) {
        return this.props.storeStateLocally({
          filterCount: instances.totalCount
        });
      }

      this.props.storeStateLocally({
        filterCount: instances.totalCount,
        filter: this.state.filter
      });

      // fetch stats when the state.filter has changed & there is a diagram
      if (this.state.diagramModel.definitions) {
        const statistics = await this.fetchStatistics();
        this.setState({statistics});
      }
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

  handleFilterReset = async fallbackFilter => {
    if (!isEqual(fallbackFilter, this.state.filter)) {
      await this.setFilter(fallbackFilter);
    }
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

  setFilterFromUrl = async () => {
    const setFilterInUrlAndState = filter => {
      this.setFilterInURL(filter);
      this.setState({filter});
    };

    const filter = parseQueryString(this.props.location.search).filter;
    // (1) empty filter
    if (!filter) {
      return setFilterInUrlAndState(DEFAULT_FILTER);
    }

    let {workflow, version, activityId, ...otherFilters} = filter;

    // (2):
    // - if there is no workflow or version (they are null or undefined) and there is an activityId, clear filter from workflow, version & activityId
    // - if there is no workflow or version and there is no activityId, reset diagramModel and statistics
    if (!workflow || !version) {
      return activityId
        ? setFilterInUrlAndState(otherFilters)
        : this.setState({filter});
    }

    // (3) validate workflow
    const isWorkflowValid = Boolean(this.state.groupedWorkflows[workflow]);

    if (!isWorkflowValid) {
      return setFilterInUrlAndState(otherFilters);
    }

    // (4):
    // - if the version is 'all' and there is an activityId, remove the activityId
    // - if the version is 'all' and there is no activityId, reset diagramModel & statistics
    if (version === 'all') {
      return activityId
        ? setFilterInUrlAndState({...otherFilters, workflow, version})
        : this.setState({
            filter: {...otherFilters, workflow, version}
          });
    }

    // check workflow & version combination
    const workflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflows[workflow],
      version
    );

    // (5) if version is invalid, remove workflow from the url filter
    if (!Boolean(workflowByVersion)) {
      return setFilterInUrlAndState(otherFilters);
    }

    const hasWorkflowChanged =
      workflow !== this.state.filter.workflow ||
      version !== this.state.filter.version;

    let {diagramModel} = this.state;

    if (hasWorkflowChanged) {
      diagramModel = await fetchDiagramModel(workflowByVersion.id);
    }

    // (6) if activityId is invalid, remove it from the url filter
    if (activityId && !diagramModel.bpmnElements[activityId]) {
      return setFilterInUrlAndState({...otherFilters, workflow, version});
    }

    // (7) if the workflow didn't change, we can immediatly update the state
    if (!hasWorkflowChanged) {
      return this.setState({
        filter
      });
    }

    // (8) Set new data in state and clear current statistics
    return this.setState({
      filter,
      diagramModel
    });
  };

  setFilterFromSelection = async activityId => {
    await this.setFilter({
      ...this.state.filter,
      activityId
    });
  };

  setFilterFromInput = async filter => {
    const sanitizedFilter = sanitizeFilter(filter);

    if (isEqual(this.state.filter, sanitizedFilter)) {
      return;
    }

    await this.setFilter(sanitizedFilter);
  };

  setFilter = async filter => {
    this.setFilterInURL(filter);
    let {workflow, version} = filter;

    const sorting = this.shouldResetSorting({filter})
      ? DEFAULT_SORTING
      : this.state.sorting;

    if (!workflow || !version || version === 'all') {
      return this.setState({
        filter,
        diagramModel: {},
        statistics: [],
        firstElement: DEFAULT_FIRST_ELEMENT,
        sorting
      });
    }

    const hasWorkflowChanged =
      workflow !== this.state.filter.workflow ||
      version !== this.state.filter.version;

    if (!hasWorkflowChanged) {
      return this.setState({
        filter,
        firstElement: DEFAULT_FIRST_ELEMENT,
        sorting
      });
    }

    const {id} = getWorkflowByVersion(
      this.state.groupedWorkflows[workflow],
      version
    );

    const diagramModel = await fetchDiagramModel(id);

    return this.setState({
      filter,
      diagramModel,
      statistics: [],
      firstElement: DEFAULT_FIRST_ELEMENT,
      sorting
    });
  };

  handleWorkflowInstancesRefresh = async () => {
    const hasDiagram = !isEmpty(this.state.diagramModel);
    this.setState({workflowInstancesLoaded: false});
    const instances = await this.fetchWorkflowInstances();

    const newState = {
      workflowInstances: instances.workflowInstances,
      filterCount: instances.totalCount,
      workflowInstancesLoaded: true
    };

    if (hasDiagram) {
      const statistics = await this.fetchStatistics();
      newState.statistics = statistics;
    }

    this.setState(newState);
  };

  render() {
    return (
      <Instances
        {...this.state}
        filter={{
          ...DEFAULT_FILTER_CONTROLLED_VALUES,
          ...decodeFields(this.state.filter)
        }}
        onFilterChange={this.setFilterFromInput}
        onFilterReset={this.handleFilterReset}
        onFirstElementChange={this.handleFirstElementChange}
        onFlowNodeSelection={this.setFilterFromSelection}
        onSort={this.handleSortingChange}
        onWorkflowInstancesRefresh={this.handleWorkflowInstancesRefresh}
      />
    );
  }
}

const WrappedInstancesContainer = withData(withSharedState(InstancesContainer));
WrappedInstancesContainer.WrappedComponent = InstancesContainer;

export default WrappedInstancesContainer;
