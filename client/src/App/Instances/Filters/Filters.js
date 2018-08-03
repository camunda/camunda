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
  parseWorkflowNames,
  parseWorkflowVersions,
  addAllVersionsOption,
  fieldParser
} from './service';
import {FIELDS, EMPTY_OPTION, ALL_VERSIONS_OPTION} from './constants';

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
      startDateBefore: PropTypes.string
    }).isRequired,
    onFilterChange: PropTypes.func,
    resetFilter: PropTypes.func,
    onWorkflowVersionChange: PropTypes.func,
    activityIds: PropTypes.arrayOf(
      PropTypes.shape({
        label: PropTypes.string,
        value: PropTypes.string
      })
    )
  };

  state = {
    groupedWorkflows: [],
    currentWorkflow: {},
    currentWorkflowVersion: EMPTY_OPTION,
    currentActivityId: EMPTY_OPTION,
    ids: ''
  };

  componentDidMount = async () => {
    this.setState({
      groupedWorkflows: await api.fetchGroupedWorkflowInstances(),
      ids: this.props.filter.ids
    });
  };

  handleWorkflowNameChange = event => {
    const {value} = event.target;
    const currentWorkflow = this.state.groupedWorkflows.find(
      item => item.bpmnProcessId === value
    );
    const version = currentWorkflow
      ? currentWorkflow.workflows[0].id
      : EMPTY_OPTION;

    this.setState(
      {
        currentWorkflow: currentWorkflow || {},
        currentWorkflowVersion: version,
        currentActivityId: EMPTY_OPTION
      },
      this.updateInstancesWorkflow
    );
  };

  updateInstancesWorkflow = async () => {
    const version = this.state.currentWorkflowVersion;
    let workflow = null;
    // we don't show a diagram when no version is available,
    // or all versions are selected
    const isValidVersion =
      !isEmpty(this.state.currentWorkflow) && version !== ALL_VERSIONS_OPTION;

    if (isValidVersion) {
      workflow = this.state.currentWorkflow.workflows.find(item => {
        return item.id === version;
      });
    }

    // needed in the Instances view for diagram display
    this.props.onWorkflowVersionChange(workflow);
  };

  handleWorkflowVersionChange = event => {
    const {value} = event.target;

    value !== EMPTY_OPTION &&
      this.setState(
        {currentWorkflowVersion: value, currentActivityId: EMPTY_OPTION},
        this.updateInstancesWorkflow
      );
  };

  handleFieldChange = event => {
    const {value, name} = event.target;
    const parsedValue = fieldParser[name](value);
    const filterValue =
      name === FIELDS.startDate.name || name === FIELDS.endDate.name
        ? {...parsedValue} // value is an object, nr: startDate: {startDateAfter: ..., startDateBefore: ...}
        : {[name]: parsedValue}; // value is an string

    this.props.onFilterChange(filterValue);
  };

  handleActivityIdChange = event => {
    event.persist();
    const {value} = event.target;
    this.setState({currentActivityId: value}, () => {
      this.handleFieldChange(event);
    });
  };

  onFieldChange = event => {
    this.setState({[event.target.name]: event.target.value});
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;
    const workflowVersions = addAllVersionsOption(
      parseWorkflowVersions(this.state.currentWorkflow.workflows)
    );

    return (
      <Panel isRounded>
        <Panel.Header isRounded>Filters</Panel.Header>
        <Panel.Body>
          <Styled.Filters>
            <Styled.Field>
              <Select
                value={this.state.currentWorkflow.bpmnProcessId || EMPTY_OPTION}
                disabled={this.state.groupedWorkflows.length === 0}
                name={FIELDS.workflowName.name}
                placeholder={FIELDS.workflowName.placeholder}
                options={parseWorkflowNames(this.state.groupedWorkflows)}
                onChange={this.handleWorkflowNameChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Select
                value={this.state.currentWorkflowVersion}
                disabled={!this.state.currentWorkflow.bpmnProcessId}
                name={FIELDS.workflowVersion.name}
                placeholder={FIELDS.workflowVersion.placeholder}
                options={workflowVersions}
                onChange={this.handleWorkflowVersionChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Textarea
                value={this.state.ids}
                name={FIELDS.ids.name}
                placeholder={FIELDS.ids.placeholder}
                onBlur={this.handleFieldChange}
                onChange={this.onFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                name={FIELDS.errorMessage.name}
                placeholder={FIELDS.errorMessage.placeholder}
                onBlur={this.handleFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                name={FIELDS.startDate.name}
                placeholder={FIELDS.startDate.placeholder}
                onBlur={this.handleFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <TextInput
                name={FIELDS.endDate.name}
                placeholder={FIELDS.endDate.placeholder}
                onBlur={this.handleFieldChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Select
                value={this.state.currentActivityId}
                disabled={
                  this.state.currentWorkflowVersion === EMPTY_OPTION ||
                  this.state.currentWorkflowVersion === ALL_VERSIONS_OPTION
                }
                name={FIELDS.activityId.name}
                placeholder={FIELDS.activityId.placeholder}
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
            onClick={this.props.resetFilter}
          >
            Reset Filters
          </Button>
        </Styled.ResetButtonContainer>
        <Panel.Footer />
      </Panel>
    );
  }
}
