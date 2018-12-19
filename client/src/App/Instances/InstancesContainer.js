import React, {Component} from 'react';
import PropTypes from 'prop-types';

import Instances from './Instances';

import withSharedState from 'modules/components/withSharedState';
import {fetchGroupedWorkflowInstances} from 'modules/api/instances';
import {DEFAULT_FILTER, PAGE_TITLE} from 'modules/constants';
import {isEqual} from 'lodash';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {getFilterQueryString, getWorkflowByVersion} from 'modules/utils/filter';
import {
  parseQueryString,
  decodeFields,
  formatGroupedWorkflowInstances
} from './service';
import {parseDiagramXML} from 'modules/utils/bpmn';

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
            currentWorkflow: {}
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
            currentWorkflow: {}
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
    // refetch nodes for new workflow + version combination
    let {diagramModel} = this.state;

    if (!isEqual(workflowByVersion, this.state.currentWorkflow)) {
      diagramModel = await this.fetchDiagramModel(workflowByVersion.id);
    }

    const isActivityIdValid = Boolean(diagramModel.bpmnElements[activityId]);

    if (activityId && !isActivityIdValid) {
      return this.setFilterInURL({...otherFilters, workflow, version});
    }

    // no activity id
    return this.setState({
      diagramModel,
      filter,
      currentWorkflow: getWorkflowByVersion(
        this.state.groupedWorkflowInstances[filter.workflow],
        filter.version
      )
    });
  };

  isValidActivityId(bpmnElements, activityId) {
    return Boolean(bpmnElements[activityId]);
  }

  updateLocalStorageFilter = () => {
    this.props.storeStateLocally({filter: this.state.filter});
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
        diagramWorkflow={this.state.currentWorkflow}
        groupedWorkflowInstances={this.state.groupedWorkflowInstances}
        onFilterChange={this.setFilterInURL}
        diagramModel={this.state.diagramModel}
      />
    );
  }
}

const WrappedInstancesContainer = withSharedState(InstancesContainer);
WrappedInstancesContainer.WrappedComponent = InstancesContainer;

export default WrappedInstancesContainer;
