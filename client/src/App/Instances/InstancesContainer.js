import React, {Component} from 'react';
import PropTypes from 'prop-types';

import Instances from './Instances';

import withSharedState from 'modules/components/withSharedState';
import {fetchGroupedWorkflowInstances} from 'modules/api/instances';
import {DEFAULT_FILTER, PAGE_TITLE} from 'modules/constants';
import {isEqual} from 'lodash';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {getFilterQueryString, getWorkflowByVersion} from 'modules/utils/filter';
import {parseQueryString, formatDiagramNodes} from './service';
import {getNodesFromXML} from 'modules/utils/bpmn';

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
      nodes: {},
      currentWorkflow: {}
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
      this.updateLocalStorageFilter();
    }
  }

  validateAndSetUrlFilter = async () => {
    const {filter} = parseQueryString(this.props.location.search);
    const validFilter = await this.getValidFilter(filter);
    // update URL with new valid filter
    if (!isEqual(filter, validFilter)) {
      this.setFilterInURL(validFilter);
    } else {
      this.setState({
        filter,
        currentWorkflow: getWorkflowByVersion(
          this.state.groupedWorkflowInstances[filter.workflow],
          filter.version
        )
      });
    }
  };

  updateLocalStorageFilter = () => {
    this.props.storeStateLocally({filter: this.state.filter});
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

    // refetch nodes for new workflow + version combination
    const nodes = isEqual(workflowByVersion, this.state.currentWorkflow)
      ? this.state.nodes
      : await this.fetchDiagramNodes(workflowByVersion.id);

    // check activityID
    if (!activityId) {
      return {...otherFilters, workflow, version};
    } else {
      const isActivityIdValid = Boolean(nodes[activityId]);
      return isActivityIdValid
        ? {...otherFilters, workflow, version, activityId}
        : {...otherFilters, workflow, version};
    }
  };

  fetchDiagramNodes = async workflowId => {
    const xml = await fetchWorkflowXML(workflowId);
    const nodes = await getNodesFromXML(xml);

    this.setState({
      nodes
    });

    return nodes;
  };

  setGroupedWorkflowInstances = (workflows = []) => {
    const groupedWorkflowInstances = workflows.reduce((obj, value) => {
      obj[value.bpmnProcessId] = {
        ...value
      };

      return obj;
    }, {});

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
        filter={this.state.filter}
        diagramWorkflow={this.state.currentWorkflow}
        groupedWorkflowInstances={this.state.groupedWorkflowInstances}
        onFilterChange={this.setFilterInURL}
        diagramNodes={formatDiagramNodes(this.state.nodes)}
      />
    );
  }
}

const WrappedInstancesContainer = withSharedState(InstancesContainer);
WrappedInstancesContainer.WrappedComponent = InstancesContainer;

export default WrappedInstancesContainer;
