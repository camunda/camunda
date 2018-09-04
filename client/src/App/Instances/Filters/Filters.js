import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import TextInput from 'modules/components/TextInput';
import Textarea from 'modules/components/Textarea';
import Select from 'modules/components/Select';
import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import {isEqual, isEmpty} from 'modules/utils';
import * as api from 'modules/api/instances';

import CheckboxGroup from './CheckboxGroup';
import * as Styled from './styled';
import {
  getOptionsForWorkflowName,
  getOptionsForWorkdflowIds,
  addAllVersionsOption,
  fieldParser,
  getFilterWithDefaults
} from './service';

import {ALL_VERSIONS_OPTION, DEFAULT_CONTROLLED_VALUES} from './constants';

const DEFAULT_FIELDS_STATE = {
  currentWorkflow: {name: '', versions: [], bpmnProcessId: ''},
  // field values
  workflowIds: '',
  activityId: '',
  startDate: '',
  endDate: '',
  // fields from filter: {} support back and fw navigation
  filter: {
    ...DEFAULT_CONTROLLED_VALUES
  }
};

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.shape({
      active: PropTypes.bool,
      incidents: PropTypes.bool,
      canceled: PropTypes.bool,
      completed: PropTypes.bool,
      activityId: PropTypes.string,
      errorMessage: PropTypes.string,
      ids: PropTypes.array,
      startDateAfter: PropTypes.string,
      startDateBefore: PropTypes.string,
      workflowIds: PropTypes.array
    }).isRequired,
    onFilterChange: PropTypes.func,
    onFilterReset: PropTypes.func,
    onWorkflowVersionChange: PropTypes.func,
    activityIds: PropTypes.arrayOf(
      PropTypes.shape({
        label: PropTypes.string,
        value: PropTypes.string
      })
    )
  };

  state = {
    workflows: [],
    ...DEFAULT_FIELDS_STATE
  };

  componentDidMount = async () => {
    const groupedWorkflows = await api.fetchGroupedWorkflowInstances();
    const workflows = groupedWorkflows.reduce((obj, value) => {
      obj[value.bpmnProcessId] = {
        ...value
      };

      return obj;
    }, {});

    this.setState({
      workflows,
      filter: {
        ...getFilterWithDefaults(this.props.filter)
      }
    });
  };

  componentDidUpdate = prevProps => {
    if (prevProps.filter !== this.props.filter)
      this.setState({
        filter: {...getFilterWithDefaults(this.props.filter)}
      });
  };

  handleWorkflowNameChange = event => {
    const {value} = event.target;
    const currentWorkflow = this.state.workflows[value];
    const version = currentWorkflow ? currentWorkflow.workflows[0].id : '';

    this.setState(
      {
        currentWorkflow: currentWorkflow || {},
        workflowIds: version,
        activityId: ''
      },
      this.updateByWorkflowVersion
    );
  };

  updateFilterOnInstancesPage = version => {
    let versions = [];

    if (version === ALL_VERSIONS_OPTION) {
      this.state.currentWorkflow.workflows.forEach(item => {
        versions.push(item.id);
      });
    } else {
      versions = version ? [version] : null;
    }

    this.props.onFilterChange({
      workflowIds: versions,
      activityId: this.state.activityId
    });
  };

  updateWorkflowOnInstancesPage = version => {
    let workflow = null;

    // we don't show a diagram when no version is available
    // or all versions are selected
    const isValidVersion =
      !isEmpty(this.state.currentWorkflow) && version !== ALL_VERSIONS_OPTION;

    if (isValidVersion) {
      workflow = this.state.currentWorkflow.workflows.find(item => {
        return item.id === version;
      });
    }

    // update workflowId in the Instances view for diagram display
    this.props.onWorkflowVersionChange(workflow);
  };

  updateByWorkflowVersion = () => {
    const version = this.state.workflowIds;

    this.updateWorkflowOnInstancesPage(version);
    this.updateFilterOnInstancesPage(version);
  };

  handleWorkflowVersionChange = event => {
    const {value} = event.target;

    value !== '' &&
      this.setState(
        {workflowIds: value, activityId: ''},
        this.updateByWorkflowVersion
      );
  };

  handleFieldChange = event => {
    const {value, name} = event.target;
    const parsedValue = fieldParser[name](value);
    const filterValue =
      name === 'startDate' || name === 'endDate'
        ? {...parsedValue} // value is an object, nr: startDate: {startDateAfter: ..., startDateBefore: ...}
        : {[name]: parsedValue}; // value is an string
    this.props.onFilterChange(filterValue);
  };

  handleActivityIdChange = event => {
    event.persist();
    const {value} = event.target;
    this.setState({activityId: value}, () => {
      this.handleFieldChange(event);
    });
  };

  handleDateFieldChange = event => {
    const {name} = event.target;

    this.setState({
      ...this.state,
      [name]: event.target.value
    });
  };

  onFieldChange = event => {
    const {name} = event.target;

    this.setState({
      filter: {
        ...this.state.filter,
        [name]: event.target.value
      }
    });
  };

  onFilterReset = () => {
    this.setState({...this.state, ...DEFAULT_FIELDS_STATE}, () => {
      this.props.onFilterReset();
    });
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;
    const workflowVersions = addAllVersionsOption(
      getOptionsForWorkdflowIds(this.state.currentWorkflow.workflows)
    );

    return (
      <Panel isRounded>
        <Panel.Header isRounded>Filters</Panel.Header>
        <Panel.Body>
          <Styled.Filters>
            <Styled.Field>
              {/* // this value is not OK */}
              <Select
                value={this.state.currentWorkflow.bpmnProcessId || ''}
                disabled={this.state.workflows.length === 0}
                name="workflowName"
                placeholder="Workflow"
                options={getOptionsForWorkflowName(this.state.workflows)}
                onChange={this.handleWorkflowNameChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Select
                value={this.state.workflowIds}
                disabled={this.state.currentWorkflow.bpmnProcessId === ''}
                name="workflowIds"
                placeholder="Workflow Version"
                options={workflowVersions}
                onChange={this.handleWorkflowVersionChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Textarea
                value={this.state.filter.ids}
                name="ids"
                placeholder="Instance Id(s) separated by space or comma"
                onBlur={this.handleFieldChange}
                onChange={this.onFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                value={this.state.filter.errorMessage}
                name="errorMessage"
                placeholder="Error Message"
                onBlur={this.handleFieldChange}
                onChange={this.onFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                value={this.state.startDate}
                name="startDate"
                placeholder="Start Date"
                onBlur={this.handleFieldChange}
                onChange={this.handleDateFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                value={this.state.endDate}
                name="endDate"
                placeholder="End Date"
                onBlur={this.handleFieldChange}
                onChange={this.handleDateFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Select
                value={this.state.activityId}
                disabled={
                  this.state.workflowIds === '' ||
                  this.state.workflowIds === ALL_VERSIONS_OPTION
                }
                name="activityId"
                placeholder="Flow Node"
                options={this.props.activityIds}
                onChange={this.handleActivityIdChange}
              />
            </Styled.Field>
            <CheckboxGroup
              type={FILTER_TYPES.RUNNING}
              filter={{
                active,
                incidents
              }}
              onChange={this.props.onFilterChange}
            />
            <CheckboxGroup
              type={FILTER_TYPES.FINISHED}
              filter={{
                completed,
                canceled
              }}
              onChange={this.props.onFilterChange}
            />
          </Styled.Filters>
        </Panel.Body>
        <Styled.ExpandButton direction={DIRECTION.LEFT} isExpanded={true} />
        <Styled.ResetButtonContainer>
          <Button
            title="Reset filters"
            disabled={isEqual(this.props.filter, DEFAULT_FILTER)}
            onClick={this.onFilterReset}
          >
            Reset Filters
          </Button>
        </Styled.ResetButtonContainer>
        <Panel.Footer />
      </Panel>
    );
  }
}
