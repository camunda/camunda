import React from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import TextInput from 'modules/components/TextInput';
import Textarea from 'modules/components/Textarea';
import Select from 'modules/components/Select';
import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import {isEqual} from 'modules/utils';
import * as api from 'modules/api/instances';

import CheckboxGroup from './CheckboxGroup';
import * as Styled from './styled';
import {
  parseWorkflowNames,
  parseWorkflowVersions,
  addAllVersionsOption,
  fieldParser,
  FIELDS
} from './service';

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.object.isRequired,
    onFilterChange: PropTypes.func,
    resetFilter: PropTypes.func,
    onWorkflowVersionChange: PropTypes.func
  };

  state = {
    groupedWorkflows: [],
    currentWorkflow: {},
    currentWorkflowVersion: '',
    currentWorkflowNode: ''
  };

  componentDidMount = async () => {
    this.setState({
      groupedWorkflows: await api.fetchGroupedWorkflowInstances()
    });
  };

  handleWorkflowNameChange = event => {
    const {value} = event.target;
    const currentWorkflow = this.state.groupedWorkflows.find(
      item => item.bpmnProcessId === value
    );
    const version = currentWorkflow ? currentWorkflow.workflows[0].id : '';

    this.setState(
      {
        currentWorkflow: currentWorkflow || {},
        currentWorkflowVersion: version
      },
      this.updateInstancesWorkflowVersion
    );
  };

  updateInstancesWorkflowVersion = async () => {
    const version = this.state.currentWorkflowVersion;

    this.props.onWorkflowVersionChange(version === 'all' ? null : version);
  };

  handleWorkflowVersionChange = event => {
    const {value} = event.target;

    value !== '' &&
      this.setState(
        {currentWorkflowVersion: value},
        this.updateInstancesWorkflowVersion
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

  handleFlowNodeChange = event => {
    console.log(event.target.value);
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
                value={this.state.currentWorkflow.bpmnProcessId || ''}
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
                name={FIELDS.ids.name}
                placeholder={FIELDS.ids.placeholder}
                onBlur={this.handleFieldChange}
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
                value={this.state.currentWorkflowNode}
                disabled={
                  this.state.currentWorkflowVersion === '' ||
                  this.state.currentWorkflowVersion === 'all'
                }
                name={FIELDS.flowNode.name}
                placeholder={FIELDS.flowNode.placeholder}
                options={[]}
                onChange={this.handleFlowNodeChange}
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
