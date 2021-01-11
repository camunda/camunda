/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {withRouter} from 'react-router';
import {Location} from 'history';

import {FiltersPanel} from './FiltersPanel';
import Button from 'modules/components/Button';
import {Input} from 'modules/components/Input';
import {
  DEFAULT_FILTER,
  FILTER_TYPES,
  DEFAULT_FILTER_CONTROLLED_VALUES,
} from 'modules/constants';

import {isEqual, isEmpty} from 'lodash';

import * as Styled from './styled';
import {
  getOptionsForWorkflowName,
  getOptionsForWorkflowVersion,
  addAllVersionsOption,
  getLastVersionOfWorkflow,
  isDateComplete,
  isDateValid,
  isVariableNameComplete,
  isVariableValueComplete,
  isVariableValueValid,
  isIdComplete,
  isIdValid,
  getFlowNodeOptions,
  sanitizeFilter,
  isBatchOperationIdComplete,
  isBatchOperationIdValid,
} from './service';
import {parseQueryString} from 'modules/utils/filter';
import {ALL_VERSIONS_OPTION, DEBOUNCE_DELAY} from './constants';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {filtersStore} from 'modules/stores/filters';
import {observer} from 'mobx-react';

type FiltersType = {
  active: boolean;
  activityId: string;
  canceled: boolean;
  completed: boolean;
  endDate: string;
  errorMessage: string;
  batchOperationId: string;
  ids: string;
  incidents: boolean;
  startDate: string;
  version: string;
  workflow: string;
  variable: {
    name?: string;
    value?: string;
  };
};
type Props = {
  filter: FiltersType;
  onInstanceClick?: () => void;
  location: Location;
};
type State = {
  filter: FiltersType;
  previewName?: string;
  previewVersion?: number;
};

const Filters = observer(
  class Filters extends React.Component<Props, State> {
    state = {
      filter: {
        active: false,
        incidents: false,
        completed: false,
        canceled: false,
        ids: '',
        errorMessage: '',
        startDate: '',
        endDate: '',
        activityId: '',
        version: '',
        workflow: '',
        variable: {name: '', value: ''},
        batchOperationId: '',
      },
    };

    componentDidMount = async () => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type '{}'.
      const {filter, name} = parseQueryString(this.props.location.search);
      if (filter) {
        this.setFilter(filter);
        this.setState({previewName: name, previewVersion: filter.version});
      }
    };

    componentDidUpdate = (prevProps: any) => {
      if (!isEqual(prevProps.filter, this.props.filter)) {
        this.setFilter(this.props.filter);
      }
    };

    componentWillUnmount = () => {
      this.resetTimer();
    };

    timer = null;

    resetTimer = () => {
      // @ts-expect-error ts-migrate(2769) FIXME: Argument of type 'null' is not assignable to param... Remove this comment to see the full error message
      clearTimeout(this.timer);
    };

    waitForTimer = async (fct: any) => {
      await this.timeout();
      fct();
    };

    timeout = () => {
      const timerPromise = (resolve: any) => {
        this.resetTimer();
        // @ts-expect-error ts-migrate(2322) FIXME: Type 'number' is not assignable to type 'null'.
        this.timer = setTimeout(resolve, DEBOUNCE_DELAY);
      };

      return new Promise(timerPromise);
    };

    setFilterState = (filter: any, callback = () => {}) => {
      this.setState(
        {
          filter: {
            ...this.state.filter,
            ...filter,
          },
        },
        callback
      );
    };

    propagateFilter = () => {
      const sanitizedFilter = sanitizeFilter(this.state.filter);
      if (!isEqual(filtersStore.state.filter, sanitizedFilter)) {
        filtersStore.setFilter(sanitizedFilter);
      }
    };

    setFilter(filter: any) {
      const {
        errorMessage,
        startDate,
        endDate,
        variable,
        ids,
        batchOperationId,
        // fields that are evaluated immediately will be overwritten by props
        ...immediateFilter
      } = filter;

      const debouncedFilter = {
        errorMessage,
        startDate,
        endDate,
        variable,
        ids,
        batchOperationId,
      };

      const sanitizedDebouncedFilter = sanitizeFilter(debouncedFilter);
      this.setFilterState({...immediateFilter, ...sanitizedDebouncedFilter});
    }

    handleWorkflowNameChange = (event: any) => {
      const {value} = event.target;
      const {groupedWorkflows} = filtersStore.state;
      // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
      const currentWorkflow = groupedWorkflows[value];
      const version = getLastVersionOfWorkflow(currentWorkflow);
      this.setFilterState(
        {workflow: value, version, activityId: ''},
        this.propagateFilter
      );
    };

    handleWorkflowVersionChange = (event: any) => {
      const {value} = event.target;

      if (value === '') {
        return;
      }

      this.setFilterState(
        {version: value, activityId: ''},
        this.propagateFilter
      );
    };

    handleControlledInputChange = (
      event: any,
      callback: any,
      options = {encodeFilterValue: false}
    ) => {
      const {value, name} = event.target;

      this.setFilterState(
        {
          [name]: options.encodeFilterValue ? encodeURIComponent(value) : value,
        },
        callback
      );
    };

    handleVariableChange = (status: any) => {
      this.setFilterState({variable: status});
    };

    onFilterReset = () => {
      this.resetTimer();
      this.setFilterState(
        {...DEFAULT_FILTER_CONTROLLED_VALUES, ...DEFAULT_FILTER},
        () => {
          if (!isEqual(DEFAULT_FILTER, filtersStore.state.filter)) {
            filtersStore.setFilter(DEFAULT_FILTER);
          }
        }
      );
    };

    getPlaceHolder(regular: any, preview: any) {
      const {groupedWorkflows} = filtersStore.state;
      const isWorkflowDataLoaded = !isEmpty(groupedWorkflows);
      if (preview && !isWorkflowDataLoaded) {
        return preview;
      } else {
        return regular;
      }
    }

    render() {
      const {
        version,
        active,
        incidents,
        canceled,
        completed,
        workflow,
        variable,
        activityId,
        errorMessage,
        startDate,
        endDate,
        ids,
        batchOperationId,
      } = this.state.filter;

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'previewVersion' does not exist on type '... Remove this comment to see the full error message
      const {previewVersion, previewName} = this.state;
      const {groupedWorkflows} = filtersStore.state;

      const isWorkflowsDataLoaded = !isEmpty(groupedWorkflows);
      const versionPlaceholder =
        previewVersion === 'all' ? `All versions` : `Version ${previewVersion}`;
      const workflowVersions =
        workflow !== '' && isWorkflowsDataLoaded
          ? addAllVersionsOption(
              // @ts-expect-error ts-migrate(2345) FIXME: Type '{ value: string; label: string; }' is not as... Remove this comment to see the full error message
              getOptionsForWorkflowVersion(groupedWorkflows[workflow].workflows)
            )
          : [];
      const {selectableFlowNodes} = instancesDiagramStore;

      return (
        <FiltersPanel>
          <Styled.Filters>
            <Styled.Field>
              <Styled.Select
                // @ts-expect-error ts-migrate(2769) FIXME: Property 'value' does not exist on type 'Intrinsic... Remove this comment to see the full error message
                value={workflow}
                disabled={!isWorkflowsDataLoaded}
                name="workflow"
                placeholder={this.getPlaceHolder('Workflow', previewName)}
                // @ts-expect-error
                options={getOptionsForWorkflowName(groupedWorkflows)}
                onChange={this.handleWorkflowNameChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Styled.Select
                // @ts-expect-error ts-migrate(2769) FIXME: Property 'value' does not exist on type 'Intrinsic... Remove this comment to see the full error message
                value={version}
                disabled={workflow === '' || !isWorkflowsDataLoaded}
                name="version"
                placeholder={this.getPlaceHolder(
                  'Workflow Version',
                  versionPlaceholder
                )}
                options={workflowVersions}
                onChange={this.handleWorkflowVersionChange}
              />
            </Styled.Field>
            <Styled.Field>
              <Styled.ValidationTextInput
                value={decodeURIComponent(ids)}
                name="ids"
                // @ts-expect-error ts-migrate(2769) FIXME: Property 'placeholder' does not exist on type 'Int... Remove this comment to see the full error message
                placeholder="Instance Id(s) separated by space or comma"
                onChange={this.handleControlledInputChange}
                checkIsComplete={isIdComplete}
                checkIsValid={isIdValid}
                onFilterChange={() => this.waitForTimer(this.propagateFilter)}
                errorMessage="Id has to be 16 to 19 digit numbers, separated by space or comma"
              >
                <Styled.Textarea />
              </Styled.ValidationTextInput>
            </Styled.Field>
            <Styled.Field>
              <Styled.ValidationTextInput
                value={decodeURIComponent(errorMessage)}
                data-testid="error-message"
                name="errorMessage"
                // @ts-expect-error ts-migrate(2769) FIXME: Property 'placeholder' does not exist on type 'Int... Remove this comment to see the full error message
                placeholder="Error Message"
                onChange={(event) =>
                  this.handleControlledInputChange(event, null, {
                    encodeFilterValue: true,
                  })
                }
                onFilterChange={() => this.waitForTimer(this.propagateFilter)}
              >
                <Input />
              </Styled.ValidationTextInput>
            </Styled.Field>
            <Styled.Field>
              {/* @ts-expect-error ts-migrate(2769) FIXME: Type '(date: any) => boolean' is not assignable to... Remove this comment to see the full error message */}
              <Styled.ValidationTextInput
                value={startDate}
                name="startDate"
                placeholder="Start Date YYYY-MM-DD hh:mm:ss"
                onChange={this.handleControlledInputChange}
                checkIsComplete={isDateComplete}
                checkIsValid={isDateValid}
                onFilterChange={() => this.waitForTimer(this.propagateFilter)}
                errorMessage="Date has to be in format YYYY-MM-DD hh:mm:ss"
              >
                <Input />
              </Styled.ValidationTextInput>
            </Styled.Field>
            <Styled.Field>
              {/* @ts-expect-error ts-migrate(2769) FIXME: Type '(date: any) => boolean' is not assignable to... Remove this comment to see the full error message */}
              <Styled.ValidationTextInput
                value={endDate}
                name="endDate"
                placeholder="End Date YYYY-MM-DD hh:mm:ss"
                onChange={this.handleControlledInputChange}
                checkIsComplete={isDateComplete}
                checkIsValid={isDateValid}
                onFilterChange={() => this.waitForTimer(this.propagateFilter)}
                errorMessage="Date has to be in format YYYY-MM-DD hh:mm:ss"
              >
                <Input />
              </Styled.ValidationTextInput>
            </Styled.Field>
            <Styled.Field>
              <Styled.Select
                // @ts-expect-error ts-migrate(2769) FIXME: Property 'value' does not exist on type 'Intrinsic... Remove this comment to see the full error message
                value={activityId}
                disabled={
                  version === '' ||
                  version === ALL_VERSIONS_OPTION ||
                  !isWorkflowsDataLoaded
                }
                name="activityId"
                placeholder={'Flow Node'}
                options={getFlowNodeOptions(selectableFlowNodes)}
                onChange={(event: any) =>
                  this.handleControlledInputChange(event, this.propagateFilter)
                }
              />
            </Styled.Field>
            <Styled.Field>
              {/* @ts-expect-error ts-migrate(2769) FIXME: Type '(variable: any) => boolean' is not assignabl... Remove this comment to see the full error message */}
              <Styled.VariableFilterInput
                variable={variable}
                onFilterChange={() => this.waitForTimer(this.propagateFilter)}
                onChange={this.handleVariableChange}
                checkIsNameComplete={isVariableNameComplete}
                checkIsValueComplete={isVariableValueComplete}
                checkIsValueValid={isVariableValueValid}
              />
            </Styled.Field>
            <Styled.Field>
              {/* @ts-expect-error ts-migrate(2769) FIXME: Type '(batchOperationId: any) => boolean' is not a... Remove this comment to see the full error message */}
              <Styled.ValidationTextInput
                value={batchOperationId}
                name="batchOperationId"
                placeholder="Operation Id"
                onChange={this.handleControlledInputChange}
                onFilterChange={() => this.waitForTimer(this.propagateFilter)}
                checkIsComplete={isBatchOperationIdComplete}
                checkIsValid={isBatchOperationIdValid}
                errorMessage="Id has to be a UUID"
              >
                <Input />
              </Styled.ValidationTextInput>
            </Styled.Field>
            <Styled.CheckboxGroup
              // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type '"running"... Remove this comment to see the full error message
              type={FILTER_TYPES.RUNNING}
              filter={{
                active,
                incidents,
              }}
              onChange={(status) =>
                this.setFilterState(status, this.propagateFilter)
              }
            />
            <Styled.CheckboxGroup
              // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type '"running"... Remove this comment to see the full error message
              type={FILTER_TYPES.FINISHED}
              filter={{
                completed,
                canceled,
              }}
              onChange={(status) =>
                this.setFilterState(status, this.propagateFilter)
              }
            />
          </Styled.Filters>
          <Styled.ResetButtonContainer>
            <Button
              title="Reset filters"
              size="small"
              disabled={isEqual(this.state.filter, {
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ...DEFAULT_FILTER,
              })}
              onClick={this.onFilterReset}
            >
              Reset Filters
            </Button>
          </Styled.ResetButtonContainer>
        </FiltersPanel>
      );
    }
  }
);

// @ts-expect-error ts-migrate(2345) FIXME: Type 'undefined' is not assignable to type 'Locati... Remove this comment to see the full error message
const WrappedFilter = withRouter(Filters);
// @ts-expect-error ts-migrate(2322) FIXME: Type 'typeof Filters' is not assignable to type 'C... Remove this comment to see the full error message
WrappedFilter.WrappedComponent = Filters;

export default WrappedFilter;
