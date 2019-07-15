/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';
import Button from 'modules/components/Button';
import {
  DEFAULT_FILTER,
  FILTER_TYPES,
  DIRECTION,
  BADGE_TYPE
} from 'modules/constants';
import {CollapsablePanelConsumer} from 'modules/contexts/CollapsablePanelContext';
import {isEqual, isEmpty, sortBy} from 'lodash';

import * as Styled from './styled';
import {
  getOptionsForWorkflowName,
  getOptionsForWorkflowVersion,
  addAllVersionsOption,
  getFilterWithDefaults,
  getLastVersionOfWorkflow
} from './service';

import {
  ALL_VERSIONS_OPTION,
  DEFAULT_CONTROLLED_VALUES,
  DEBOUNCE_DELAY
} from './constants';

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
      version: PropTypes.string,
      workflow: PropTypes.string,
      variable: PropTypes.shape({
        name: PropTypes.string,
        value: PropTypes.string
      })
    }).isRequired,
    filterCount: PropTypes.number.isRequired,
    onFilterChange: PropTypes.func.isRequired,
    onFilterReset: PropTypes.func.isRequired,
    selectableFlowNodes: PropTypes.arrayOf(PropTypes.object),
    groupedWorkflows: PropTypes.object
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
    if (!isEqual(prevProps.filter, this.props.filter)) {
      this.setState({
        filter: {...getFilterWithDefaults(this.props.filter)}
      });
    }
  };

  componentWillUnmount = () => {
    this.resetTimer();
  };

  resetTimer = () => {
    clearTimeout(this.timer);
  };

  timer = null;

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
    const currentWorkflow = this.props.groupedWorkflows[value];
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
      this.handleInputChange(event);
    }

    this.props.onFilterChange({[name]: value});
  };

  // handles input changes and triggers onFilterChange, when
  // no input changes were made for a given timeout delay
  handleInputChangeDebounced = event => {
    const {value, name} = event.target;

    this.handleInputChange(event);

    this.resetTimer();

    this.timer = setTimeout(() => {
      this.props.onFilterChange({[name]: value});
    }, DEBOUNCE_DELAY);
  };

  // handler for controlled inputs
  handleInputChange = event => {
    const {value, name} = event.target;

    this.setState({
      filter: {
        ...this.state.filter,
        [name]: value
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

  sortByFlowNodeLabel = flowNodes => {
    return sortBy(flowNodes, flowNode => flowNode.label.toLowerCase());
  };

  addValueAndLabel = bpmnElement => {
    return {
      ...bpmnElement,
      value: bpmnElement.id,
      label: bpmnElement.name
        ? bpmnElement.name
        : 'Unnamed' + bpmnElement.$type.split(':')[1].replace(/([A-Z])/g, ' $1')
    };
  };

  sortAndModify = bpmnElements => {
    if (bpmnElements.length < 1) {
      return [];
    }

    const named = [];
    const unnamed = [];

    bpmnElements.forEach(bpmnElement => {
      const enhancedElement = this.addValueAndLabel(bpmnElement);

      if (enhancedElement.name) {
        named.push(enhancedElement);
      } else {
        unnamed.push(enhancedElement);
      }
    });

    return [
      ...this.sortByFlowNodeLabel(named),
      ...this.sortByFlowNodeLabel(unnamed)
    ];
  };

  render() {
    const {active, incidents, canceled, completed} = this.props.filter;
    const isWorkflowsDataLoaded = !isEmpty(this.props.groupedWorkflows);
    const workflowVersions =
      this.state.filter.workflow !== '' && isWorkflowsDataLoaded
        ? addAllVersionsOption(
            getOptionsForWorkflowVersion(
              this.props.groupedWorkflows[this.state.filter.workflow].workflows
            )
          )
        : [];

    return (
      <CollapsablePanelConsumer>
        {context => (
          <CollapsablePanel
            isCollapsed={context.isFiltersCollapsed}
            onCollapse={context.toggleFilters}
            maxWidth={320}
            expandButton={
              <Styled.VerticalButton label="Filters">
                <Badge type={BADGE_TYPE.FILTERS}>
                  {this.props.filterCount}
                </Badge>
              </Styled.VerticalButton>
            }
            collapseButton={
              <Styled.CollapseButton
                direction={DIRECTION.LEFT}
                isExpanded={true}
                title="Collapse Filters"
              />
            }
          >
            <Styled.FiltersHeader>
              Filters
              <Badge type={BADGE_TYPE.FILTERS}>{this.props.filterCount}</Badge>
            </Styled.FiltersHeader>
            <CollapsablePanel.Body>
              <Styled.Filters>
                <Fragment>
                  <Styled.Field>
                    <Styled.Select
                      value={this.state.filter.workflow}
                      disabled={isEmpty(this.props.groupedWorkflows)}
                      name="workflow"
                      placeholder="Workflow"
                      options={getOptionsForWorkflowName(
                        this.props.groupedWorkflows
                      )}
                      onChange={this.handleWorkflowNameChange}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.Select
                      value={this.state.filter.version}
                      disabled={this.state.filter.workflow === ''}
                      name="version"
                      placeholder="Workflow Version"
                      options={workflowVersions}
                      onChange={this.handleWorkflowVersionChange}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.Textarea
                      value={this.state.filter.ids}
                      name="ids"
                      placeholder="Instance Id(s) separated by space or comma"
                      onBlur={this.handleFieldChange}
                      onChange={this.handleInputChange}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.TextInput
                      value={this.state.filter.errorMessage}
                      name="errorMessage"
                      placeholder="Error Message"
                      onChange={this.handleInputChangeDebounced}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.TextInput
                      value={this.state.filter.startDate}
                      name="startDate"
                      placeholder="Start Date"
                      onBlur={this.handleFieldChange}
                      onChange={this.handleInputChange}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.TextInput
                      value={this.state.filter.endDate}
                      name="endDate"
                      placeholder="End Date"
                      onBlur={this.handleFieldChange}
                      onChange={this.handleInputChange}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.Select
                      value={this.state.filter.activityId}
                      disabled={
                        this.state.filter.version === '' ||
                        this.state.filter.version === ALL_VERSIONS_OPTION
                      }
                      name="activityId"
                      placeholder="Flow Node"
                      options={this.sortAndModify(
                        this.props.selectableFlowNodes
                      )}
                      onChange={this.handleFieldChange}
                    />
                  </Styled.Field>
                  <Styled.Field>
                    <Styled.VariableFilterInput
                      variable={this.state.filter.variable}
                      onFilterChange={this.props.onFilterChange}
                    />
                  </Styled.Field>
                  <Styled.CheckboxGroup
                    type={FILTER_TYPES.RUNNING}
                    filter={{
                      active,
                      incidents
                    }}
                    onChange={this.props.onFilterChange}
                  />
                  <Styled.CheckboxGroup
                    type={FILTER_TYPES.FINISHED}
                    filter={{
                      completed,
                      canceled
                    }}
                    onChange={this.props.onFilterChange}
                  />
                </Fragment>
              </Styled.Filters>
            </CollapsablePanel.Body>
            <Styled.ResetButtonContainer>
              <Button
                title="Reset filters"
                disabled={isEqual(this.props.filter, DEFAULT_FILTER)}
                onClick={this.onFilterReset}
              >
                Reset Filters
              </Button>
            </Styled.ResetButtonContainer>
            <CollapsablePanel.Footer />
          </CollapsablePanel>
        )}
      </CollapsablePanelConsumer>
    );
  }
}
