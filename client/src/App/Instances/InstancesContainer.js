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
  LOADING_STATE,
  SUBSCRIPTION_TOPIC
} from 'modules/constants';

import {fetchGroupedWorkflows} from 'modules/api/instances';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
  getFilterQueryString,
  getWorkflowByVersion
} from 'modules/utils/filter';
import {formatGroupedWorkflows} from 'modules/utils/instance';

import Instances from './Instances';
import {parseQueryString, decodeFields} from './service';

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
      firstElement: DEFAULT_FIRST_ELEMENT,
      sorting: DEFAULT_SORTING
    };
    this.subscriptions = {
      LOAD_STATE_DEFINITIONS: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          this.setState({diagramModel: response, statistics: []});
          this.fetchStatistics();
        }
      },
      LOAD_STATE_STATISTICS: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          this.setState({statistics: response.statistics});
        }
      },
      LOAD_LIST_INSTANCES: ({response, state}) => {
        if (state === LOADING_STATE.LOADED) {
          this.setState({
            workflowInstances: response.workflowInstances,
            filterCount: response.totalCount
          });
          this.props.storeStateLocally({
            filterCount: response.totalCount
          });
        }
      },
      REFRESH_AFTER_OPERATION: ({state, response, error}) => {
        if (state === LOADING_STATE.LOADED) {
          const {
            LOAD_LIST_INSTANCES,
            LOAD_STATE_STATISTICS
          } = SUBSCRIPTION_TOPIC;
          this.setState({
            workflowInstances: response[LOAD_LIST_INSTANCES].workflowInstances,
            filterCount: response[LOAD_LIST_INSTANCES].totalCount,
            ...(response[LOAD_STATE_STATISTICS] && {
              statistics: response[LOAD_STATE_STATISTICS].statistics
            })
          });
        }
      }
    };
  }

  async componentDidMount() {
    document.title = PAGE_TITLE.INSTANCES;
    this.props.dataManager.subscribe(this.subscriptions);

    const groupedWorkflows = formatGroupedWorkflows(
      await fetchGroupedWorkflows()
    );

    const filterFromURL = parseQueryString(this.props.location.search).filter;
    const sanitizedFilterfromURL = this.sanitizeFilter(
      filterFromURL,
      groupedWorkflows
    );

    if (!isEqual(sanitizedFilterfromURL, filterFromURL)) {
      this.setFilterInURL(sanitizedFilterfromURL);
    }

    this.fetchWorkflowInstances(sanitizedFilterfromURL, groupedWorkflows);

    if (sanitizedFilterfromURL.workflow && sanitizedFilterfromURL.version) {
      this.fetchWorkflowXML(
        groupedWorkflows,
        sanitizedFilterfromURL.workflow,
        sanitizedFilterfromURL.version
      );
    }

    this.setState({
      groupedWorkflows: groupedWorkflows,
      filter: sanitizedFilterfromURL
    });
  }

  async componentDidUpdate(prevProps, prevState) {
    const {filter, groupedWorkflows, diagramModel} = this.state;

    const filterFromURL = this.sanitizeFilter(
      parseQueryString(this.props.location.search).filter,
      groupedWorkflows,
      diagramModel
    );

    const prevFilterFromURL = this.sanitizeFilter(
      parseQueryString(prevProps.location.search).filter,
      groupedWorkflows,
      diagramModel
    );

    const hasURLChanged = !isEqual(filterFromURL, prevFilterFromURL);

    const hasFilterChanged = !isEqual(prevState.filter, filter);

    // caused by browser backwards/forward btn or manual URL manipulation
    if (hasURLChanged) {
      this.setFilterInURL(filterFromURL);
      this.setState({filter: filterFromURL});
    }

    if (hasFilterChanged) {
      this.props.storeStateLocally({
        filter
      });
      // set filter in URL after changes in the UI
      if (!isEqual(filter, filterFromURL)) {
        this.setFilterInURL(filter);
      }
    }

    // caused by interaction with version or workflow filter
    const hasWorkflowChanged =
      prevState.filter.workflow !== filter.workflow ||
      prevState.filter.version !== filter.version;

    if (hasFilterChanged && hasWorkflowChanged) {
      this.fetchWorkflowXML(groupedWorkflows, filter.workflow, filter.version);
    }

    // caused by interaction with non version or workflow filter
    if (hasFilterChanged && !hasWorkflowChanged && diagramModel.definitions) {
      this.fetchStatistics();
    }

    // caused by interaction with instances list
    const hasFirstElementChanged =
      prevState.firstElement !== this.state.firstElement;
    const hasSortingChanged = !isEqual(prevState.sorting, this.state.sorting);

    if (hasFilterChanged || hasSortingChanged || hasFirstElementChanged) {
      this.fetchWorkflowInstances(filter, groupedWorkflows);
    }
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  fetchWorkflowXML(groupedWorkflows, workflow, version) {
    if (version === 'all' || !version) {
      return;
    }
    const workflowId = getWorkflowByVersion(groupedWorkflows[workflow], version)
      .id;

    this.props.dataManager.getWorkflowXML(workflowId);
  }

  fetchWorkflowInstances = async (filter, groupedWorkflows) => {
    const {sorting, firstElement} = this.state;

    this.props.dataManager.getWorkflowInstances({
      queries: [
        parseFilterForRequest(
          decodeFields(getFilterWithWorkflowIds(filter, groupedWorkflows))
        )
      ],
      sorting,
      firstResult: firstElement,
      maxResults: DEFAULT_MAX_RESULTS
    });
  };

  fetchStatistics = () => {
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

    return this.props.dataManager.getWorkflowInstancesStatistics({
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
    const {history} = this.props;
    return (
      history.location.search !== getFilterQueryString(filter) &&
      history.push({
        pathname: this.props.location.pathname,
        search: getFilterQueryString(filter)
      })
    );
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

  sanitizeFilter = (
    filter,
    groupedWorkflows,
    diagramModel = {bpmnElements: {}}
  ) => {
    // (1) empty filter
    if (!filter) {
      return DEFAULT_FILTER;
    }

    let {workflow, version, activityId, ...otherFilters} = filter;

    // (2):
    // - if there is no workflow or version (they are null or undefined) and there is an activityId, clear filter from workflow, version & activityId
    // - if there is no workflow or version and there is no activityId, reset diagramModel and statistics
    if (activityId && (!workflow || !version)) {
      return otherFilters;
    }

    // (3) validate workflow
    const isWorkflowValid = Boolean(groupedWorkflows[workflow]);
    if (!isWorkflowValid) {
      return otherFilters;
    }

    // (4):
    // - if the version is 'all' and there is an activityId, remove the activityId
    // - if the version is 'all' and there is no activityId, reset diagramModel & statistics
    if (version === 'all' && activityId) {
      return {...otherFilters, workflow, version};
    }

    const workflowByVersion = getWorkflowByVersion(
      groupedWorkflows[workflow],
      version
    );

    // (5) if version is invalid, remove workflow from the url filter
    if (!Boolean(workflowByVersion)) {
      return otherFilters;
    }

    // (6) if activityId is invalid, remove it from the url filter
    if (
      activityId &&
      diagramModel.bpmnElements &&
      !diagramModel.bpmnElements[activityId]
    ) {
      return {...otherFilters, workflow, version};
    }

    return filter;
  };

  setFilterFromSelection = async activityId => {
    return this.setFilter({
      ...this.state.filter,
      activityId: activityId ? activityId : ''
    });
  };

  setFilterFromInput = filter => {
    if (isEqual(this.state.filter, filter)) {
      return;
    }
    this.setFilter(filter);
  };

  setFilter = filter => {
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

    return this.setState({filter});
  };

  render() {
    return (
      <Instances
        {...this.state}
        filter={decodeFields(this.state.filter)}
        onFilterChange={this.setFilterFromInput}
        onFilterReset={this.handleFilterReset}
        onFirstElementChange={this.handleFirstElementChange}
        onFlowNodeSelection={this.setFilterFromSelection}
        onSort={this.handleSortingChange}
      />
    );
  }
}

const WrappedInstancesContainer = withData(withSharedState(InstancesContainer));
WrappedInstancesContainer.WrappedComponent = InstancesContainer;

export default WrappedInstancesContainer;
