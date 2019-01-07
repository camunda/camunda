import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {isEqual, isEmpty} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import {fetchGroupedWorkflowInstances} from 'modules/api/instances';
import {DEFAULT_FILTER, PAGE_TITLE} from 'modules/constants';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {fetchWorkflowInstancesStatistics} from 'modules/api/instances';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
  getFilterQueryString,
  getWorkflowByVersion
} from 'modules/utils/filter';
import {parseDiagramXML} from 'modules/utils/bpmn';

import Instances from './Instances';
import {
  parseQueryString,
  decodeFields,
  formatGroupedWorkflowInstances
} from './service';
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
      filter: {},
      groupedWorkflowInstances: {},
      diagramModel: {},
      statistics: []
    };
  }

  async componentDidMount() {
    document.title = PAGE_TITLE.INSTANCES;
    const groupedWorkflows = await fetchGroupedWorkflowInstances();
    this.setGroupedWorkflowInstances(groupedWorkflows);
    this.handleUrlFilter();
  }

  async componentDidUpdate(prevProps, prevState) {
    const prevUrlFilter = parseQueryString(prevProps.location.search).filter;
    const currentUrlFilter = parseQueryString(this.props.location.search)
      .filter;

    // filter in url has changed
    if (!isEqual(prevUrlFilter, currentUrlFilter)) {
      return this.handleUrlFilter();
    }

    // filter in state has changed & there is a diagram
    if (
      this.state.diagramModel.definitions &&
      !isEqual(this.state.filter, prevState.filter)
    ) {
      const statistics = await this.fetchStatistics();
      this.setState({statistics});
    }
  }

  /**
   * Sanitizes the current filter in url and updates accordingly either
   * the url or state and the local storage.
   */
  handleUrlFilter = async () => {
    const [isValidUrlFilter, update] = await this.sanitizeStateAndUrl();

    if (isValidUrlFilter) {
      return this.setState(update, this.updateLocalStorageFilter);
    }

    this.setFilterInURL(update.filter);
    this.updateLocalStorageFilter();
  };

  /**
   * Parses the url filter and returns an update object containing the sanitized filter.
   * If the url filter is valid, the update object can be used to cleanup the state.
   * If the url filter is not valid, the update contains the update object
   * can be used to cleanup url filter.
   * @returns [isValidUrlFilter, update]
   */
  sanitizeStateAndUrl = async () => {
    const filter = parseQueryString(this.props.location.search).filter;

    // (1) empty filter
    if (!filter) {
      return [false, {filter: DEFAULT_FILTER}];
    }

    let {workflow, version, activityId, ...otherFilters} = filter;

    // (2):
    // - if there is no workflow or version and there is an activityId, clear filter from workflow, version & activityId
    // - if there is no workflow or version and there is no activityId, reset diagramModel and statistics
    if (!workflow || !version) {
      return activityId
        ? [false, {filter: otherFilters}]
        : [true, {filter, diagramModel: {}, statistics: []}];
    }

    // (3) validate workflow
    const isWorkflowValid = Boolean(
      this.state.groupedWorkflowInstances[workflow]
    );

    // (3) if the workflow is invalid, remove it from the url filter
    if (!isWorkflowValid) {
      return [false, {filter: otherFilters}];
    }

    // (4):
    // - if the version is 'all' and there is an activityId, remove the activityId from the url filter
    // - if the version is 'all' and there is no activityId, reset diagramModel & statistics
    if (version === 'all') {
      return activityId
        ? [false, {filter: {...otherFilters, workflow, version}}]
        : [
            true,
            {
              filter,
              diagramModel: {},
              statistics: []
            }
          ];
    }

    // check workflow & version combination
    const workflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflowInstances[workflow],
      version
    );

    // (5) if version is invalid, remove workflow from the url filter
    if (!Boolean(workflowByVersion)) {
      return [false, {filter: otherFilters}];
    }

    const currentWorkflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflowInstances[this.state.filter.workflow],
      this.state.filter.version
    );

    const hasWorkflowChanged = !isEqual(
      workflowByVersion,
      currentWorkflowByVersion
    );

    let {diagramModel} = this.state;

    if (hasWorkflowChanged) {
      diagramModel = await this.fetchDiagramModel(workflowByVersion.id);
    }

    // (6) if activityId is invalid, remove it from the url filter
    if (activityId && !diagramModel.bpmnElements[activityId]) {
      return [false, {filter: {...otherFilters, workflow, version}}];
    }

    // (7) if the workflow didn't change, we can immediatly update the state
    if (!hasWorkflowChanged) {
      return [true, {filter}];
    }

    // (8) Set new data in state and clear current statistics
    return [true, {filter, diagramModel, statistics: []}];
  };

  isValidActivityId(bpmnElements, activityId) {
    return Boolean(bpmnElements[activityId]);
  }

  updateLocalStorageFilter = () => {
    this.props.storeStateLocally({filter: this.state.filter});
  };

  fetchStatistics = async () => {
    const {filter, groupedWorkflowInstances} = this.state;
    const workflowByVersion = getWorkflowByVersion(
      groupedWorkflowInstances[filter.workflow],
      filter.version
    );

    if (isEmpty(workflowByVersion)) {
      return;
    }

    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      filter,
      groupedWorkflowInstances
    );

    return await fetchWorkflowInstancesStatistics({
      queries: [parseFilterForRequest(decodeFields(filterWithWorkflowIds))]
    });
  };

  fetchDiagramModel = async workflowId => {
    const xml = await fetchWorkflowXML(workflowId);
    return await parseDiagramXML(xml);
  };

  setGroupedWorkflowInstances = (workflows = []) => {
    const groupedWorkflowInstances = formatGroupedWorkflowInstances(workflows);

    this.setState({groupedWorkflowInstances});
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  render() {
    console.log(this.state.statistics);
    return (
      <Instances
        filter={decodeFields(this.state.filter)}
        groupedWorkflowInstances={this.state.groupedWorkflowInstances}
        onFilterChange={this.setFilterInURL}
        diagramModel={this.state.diagramModel}
        statistics={this.state.statistics}
      />
    );
  }
}

const WrappedInstancesContainer = withSharedState(InstancesContainer);
WrappedInstancesContainer.WrappedComponent = InstancesContainer;

export default WrappedInstancesContainer;
