import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import Panel from 'modules/components/Panel';
import Button from 'modules/components/Button';
import TextInput from 'modules/components/TextInput';
import Textarea from 'modules/components/Textarea';
import Select from 'modules/components/Select';
import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import {isEqual, isEmpty} from 'modules/utils';

import CheckboxGroup from './CheckboxGroup';
import * as Styled from './styled';
import {
  getOptionsForWorkflowName,
  getOptionsForWorkflowVersion,
  addAllVersionsOption,
  getFilterWithDefaults,
  getLastVersionOfWorkflow
} from './service';

import {ALL_VERSIONS_OPTION, DEFAULT_CONTROLLED_VALUES} from './constants';

export default class Filters extends React.Component {
  static propTypes = {
    filter: PropTypes.shape({
      active: PropTypes.bool,
      activityId: PropTypes.string,
      canceled: PropTypes.bool,
      completed: PropTypes.bool,
      endDate: PropTypes.string,
      errorMessage: PropTypes.string,
      ids: PropTypes.string,
      incidents: PropTypes.bool,
      startDate: PropTypes.string,
      version: PropTypes.string
    }).isRequired,
    onFilterChange: PropTypes.func.isRequired,
    onFilterReset: PropTypes.func.isRequired,
    activityIds: PropTypes.arrayOf(
      PropTypes.shape({
        label: PropTypes.string,
        value: PropTypes.string
      })
    ),
    groupedWorkflowInstances: PropTypes.object
  };

  state = {
    filter: {
      ...DEFAULT_CONTROLLED_VALUES
    }
  };

  componentDidMount = async () => {
    this.setState({
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

  updateByWorkflowVersion = () => {
    const version = this.state.filter.version;

    this.props.onFilterChange({
      workflow: this.state.filter.workflow,
      version,
      activityId: ''
    });
  };

  handleWorkflowNameChange = event => {
    const {value} = event.target;
    const currentWorkflow = this.props.groupedWorkflowInstances[value];
    const version = getLastVersionOfWorkflow(currentWorkflow);

    this.setState(
      {
        filter: {
          ...this.state.filter,
          workflow: value,
          version,
          activityId: ''
        }
      },
      this.updateByWorkflowVersion
    );
  };

  handleWorkflowVersionChange = event => {
    const {value} = event.target;
    value !== '' &&
      this.setState(
        {
          filter: {...this.state.filter, version: value, activityId: ''}
        },
        this.updateByWorkflowVersion
      );
  };

  handleFieldChange = event => {
    const {value, name} = event.target;

    if (this.state.filter[name] !== value) {
      this.setState({
        filter: {
          ...this.state.filter,
          [name]: value
        }
      });
    }

    this.props.onFilterChange({[name]: value});
  };

  // handler for controlled inputs
  handleInputChange = event => {
    const {name} = event.target;

    this.setState({
      filter: {
        ...this.state.filter,
        [name]: event.target.value
      }
    });
  };

  onFilterReset = () => {
    this.setState(
      {
        filter: {
          ...DEFAULT_CONTROLLED_VALUES
        }
      },
      () => {
        //reset updates on Instances page
        this.props.onFilterReset();
      }
    );
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;
    const isWorkflowsDataLoaded = !isEmpty(this.props.groupedWorkflowInstances);
    const workflowVersions =
      this.state.filter.workflow !== '' && isWorkflowsDataLoaded
        ? addAllVersionsOption(
            getOptionsForWorkflowVersion(
              this.props.groupedWorkflowInstances[this.state.filter.workflow]
                .workflows
            )
          )
        : [];

    return (
      <Panel isRounded>
        <Panel.Header isRounded>Filters</Panel.Header>
        <Panel.Body>
          <Styled.Filters>
            {!isWorkflowsDataLoaded ? null : (
              <Fragment>
                <Styled.Field>
                  <Select
                    value={this.state.filter.workflow}
                    disabled={isEmpty(this.props.groupedWorkflowInstances)}
                    name="workflow"
                    placeholder="Workflow"
                    options={getOptionsForWorkflowName(
                      this.props.groupedWorkflowInstances
                    )}
                    onChange={this.handleWorkflowNameChange}
                  />
                </Styled.Field>
                <Styled.Field>
                  <Select
                    value={this.state.filter.version}
                    disabled={this.state.filter.workflow === ''}
                    name="version"
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
                    onChange={this.handleInputChange}
                  />
                </Styled.Field>
                <Styled.Field>
                  <TextInput
                    value={this.state.filter.errorMessage}
                    name="errorMessage"
                    placeholder="Error Message"
                    onBlur={this.handleFieldChange}
                    onChange={this.handleInputChange}
                  />
                </Styled.Field>
                <Styled.Field>
                  <TextInput
                    value={this.state.filter.startDate}
                    name="startDate"
                    placeholder="Start Date"
                    onBlur={this.handleFieldChange}
                    onChange={this.handleInputChange}
                  />
                </Styled.Field>
                <Styled.Field>
                  <TextInput
                    value={this.state.filter.endDate}
                    name="endDate"
                    placeholder="End Date"
                    onBlur={this.handleFieldChange}
                    onChange={this.handleInputChange}
                  />
                </Styled.Field>
                <Styled.Field>
                  <Select
                    value={this.state.filter.activityId}
                    disabled={
                      this.state.filter.version === '' ||
                      this.state.filter.version === ALL_VERSIONS_OPTION
                    }
                    name="activityId"
                    placeholder="Flow Node"
                    options={this.props.activityIds}
                    onChange={this.handleFieldChange}
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
              </Fragment>
            )}
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
