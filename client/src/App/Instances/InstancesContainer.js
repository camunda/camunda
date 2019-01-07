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
    await this.validateAndSetUrlFilter();
    this.updateLocalStorageFilter();
  }

  async componentDidUpdate(prevProps, prevState) {
    // filter in url has changed
    if (
      !isEqual(
        parseQueryString(prevProps.location.search).filter,
        parseQueryString(this.props.location.search).filter
      )
    ) {
      await this.validateAndSetUrlFilter();
      return this.updateLocalStorageFilter();
    }

    // fetch statistics
    if (!isEqual(this.state.filter, prevState.filter)) {
      const statistics = await this.fetchStatistics();
      this.setState({statistics});
    }
  }

  // TODO: set currentWorkflow in Instances
  validateAndSetUrlFilter = async () => {
    const {filter} = parseQueryString(this.props.location.search);

    // (1) empty filter
    if (!filter) {
      return this.setFilterInURL(DEFAULT_FILTER);
    }

    let {workflow, version, activityId, ...otherFilters} = filter;

    // (2) filter has no workflow
    if (!workflow || (workflow && !version)) {
      return activityId
        ? this.setFilterInURL(otherFilters)
        : this.setState({
            filter,
            diagramModel: {},
            statistics: []
          });
    }

    // (3) validate workflow
    const isWorkflowValid = Boolean(
      this.state.groupedWorkflowInstances[workflow]
    );

    if (!isWorkflowValid) {
      return this.setFilterInURL(otherFilters);
    }

    if (version === 'all') {
      // set filter in url without activity id
      return activityId
        ? this.setFilterInURL({...otherFilters, workflow, version})
        : this.setState({
            filter,
            diagramModel: {},
            statistics: []
          });
    }

    // check workflow & version combination
    const workflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflowInstances[workflow],
      version
    );

    // version is not valid for workflow
    if (!Boolean(workflowByVersion)) {
      return this.setFilterInURL(otherFilters);
    }

    // (4) diagramModel
    const currentWorkflowByVersion = getWorkflowByVersion(
      this.state.groupedWorkflowInstances[this.state.filter.workflow],
      this.state.filter.version
    );

    const hasWorkflowChanged = !isEqual(
      workflowByVersion,
      currentWorkflowByVersion
    );

    // if activityId is invalid, remove it from the url filter
    let {diagramModel} = this.state;

    if (hasWorkflowChanged) {
      diagramModel = await this.fetchDiagramModel(workflowByVersion.id);
    }

    if (activityId && !diagramModel.bpmnElements[activityId]) {
      return this.setFilterInURL({...otherFilters, workflow, version});
    }

    // if the workflow didn't change, we can immediatly update the state
    if (!hasWorkflowChanged) {
      return this.setState({
        filter
      });
    }

    // Set new data in state and clear current statistics
    this.setState({diagramModel, statistics: [], filter});
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
      queries: [parseFilterForRequest(filterWithWorkflowIds)]
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
