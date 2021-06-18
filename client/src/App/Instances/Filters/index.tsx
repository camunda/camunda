/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Form, Field} from 'react-final-form';
import {useHistory} from 'react-router-dom';
import {isEqual} from 'lodash';
import {
  FiltersForm,
  Row,
  VariableRow,
  ResetButtonContainer,
  Fields,
} from './styled';
import {ProcessField} from './ProcessField';
import {ProcessVersionField} from './ProcessVersionField';
import {FlowNodeField} from './FlowNodeField';
import Textarea from 'modules/components/Textarea';
import {Input} from 'modules/components/Input';
import {CheckboxGroup} from './CheckboxGroup';
import Button from 'modules/components/Button';
import {InjectAriaInvalid} from './InjectAriaInvalid';
import {AutoSubmit} from './AutoSubmit';
import {Error, VariableError} from './Error';
import {
  validateDateCharacters,
  validateDateComplete,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameComplete,
  validateVariableValueComplete,
  validateIdsCharacters,
  validateIdsNotTooLong,
  validatesIdsComplete,
} from './validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {
  getFilters,
  updateFiltersSearchString,
  FiltersType,
} from 'modules/utils/filter';
import {storeStateLocally} from 'modules/utils/localStorage';
import {FiltersPanel} from './FiltersPanel';
import {HAS_PARENT_INSTANCE_ID} from 'modules/feature-flags';

const Filters: React.FC = () => {
  const history = useHistory();
  const initialValues: FiltersType = {
    active: true,
    incidents: true,
  };

  function setFiltersToURL(filters: FiltersType) {
    history.push({
      ...history.location,
      search: updateFiltersSearchString(history.location.search, filters),
    });
  }

  useEffect(() => {
    storeStateLocally({
      filters: getFilters(history.location.search),
    });
  }, [history.location.search]);

  return (
    <FiltersPanel>
      <Form<FiltersType>
        onSubmit={(values) => {
          setFiltersToURL(values);
        }}
        initialValues={getFilters(history.location.search)}
      >
        {({handleSubmit, form, values}) => (
          <FiltersForm onSubmit={handleSubmit}>
            <Fields>
              <AutoSubmit
                fieldsToSkipTimeout={[
                  'process',
                  'version',
                  'flowNodeId',
                  'active',
                  'incidents',
                  'completed',
                  'canceled',
                ]}
              />
              <Row>
                <ProcessField />
              </Row>
              <Row>
                <ProcessVersionField />
              </Row>
              <Row>
                <Field
                  name="ids"
                  validate={mergeValidators(
                    validateIdsCharacters,
                    validateIdsNotTooLong,
                    validatesIdsComplete
                  )}
                >
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Textarea
                        {...input}
                        placeholder="Instance Id(s) separated by space or comma"
                      />
                    </InjectAriaInvalid>
                  )}
                </Field>
                <Error name="ids" />
              </Row>
              {HAS_PARENT_INSTANCE_ID && (
                <Row>
                  <Field name="parentInstanceId">
                    {({input}) => (
                      <Input {...input} placeholder="Parent Instance Id" />
                    )}
                  </Field>
                </Row>
              )}
              <Row>
                <Field name="errorMessage">
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Input {...input} placeholder="Error Message" />
                    </InjectAriaInvalid>
                  )}
                </Field>
              </Row>
              <Row>
                <Field
                  name="startDate"
                  validate={mergeValidators(
                    validateDateCharacters,
                    validateDateComplete
                  )}
                >
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Input
                        {...input}
                        placeholder="Start Date YYYY-MM-DD hh:mm:ss"
                      />
                    </InjectAriaInvalid>
                  )}
                </Field>
                <Error name="startDate" />
              </Row>
              <Row>
                <Field
                  name="endDate"
                  validate={mergeValidators(
                    validateDateCharacters,
                    validateDateComplete
                  )}
                >
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Input
                        {...input}
                        placeholder="End Date YYYY-MM-DD hh:mm:ss"
                      />
                    </InjectAriaInvalid>
                  )}
                </Field>
                <Error name="endDate" />
              </Row>
              <Row>
                <FlowNodeField />
              </Row>
              <VariableRow>
                <Field
                  name="variableName"
                  validate={validateVariableNameComplete}
                >
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Input
                        {...input}
                        onChange={(
                          event: React.ChangeEvent<HTMLInputElement>
                        ) => {
                          input.onChange(event);

                          if (event.target.value === '') {
                            form.submit();
                          }
                        }}
                        placeholder="Variable"
                      />
                    </InjectAriaInvalid>
                  )}
                </Field>
                <Field
                  name="variableValue"
                  validate={validateVariableValueComplete}
                >
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Input
                        {...input}
                        onChange={(
                          event: React.ChangeEvent<HTMLInputElement>
                        ) => {
                          input.onChange(event);

                          if (event.target.value === '') {
                            form.submit();
                          }
                        }}
                        placeholder="Value"
                      />
                    </InjectAriaInvalid>
                  )}
                </Field>
                <VariableError names={['variableName', 'variableValue']} />
              </VariableRow>
              <Row>
                <Field
                  name="operationId"
                  validate={mergeValidators(
                    validateOperationIdCharacters,
                    validateOperationIdComplete
                  )}
                >
                  {({input}) => (
                    <InjectAriaInvalid name={input.name}>
                      <Input {...input} placeholder="Operation Id" />
                    </InjectAriaInvalid>
                  )}
                </Field>
                <Error name="operationId" />
              </Row>
              <Row>
                <CheckboxGroup
                  groupLabel="Running Instances"
                  items={[
                    {
                      label: 'Active',
                      name: 'active',
                    },
                    {
                      label: 'Incidents',
                      name: 'incidents',
                    },
                  ]}
                />
              </Row>
              <Row>
                <CheckboxGroup
                  groupLabel="Finished Instances"
                  items={[
                    {
                      label: 'Completed',
                      name: 'completed',
                    },
                    {
                      label: 'Canceled',
                      name: 'canceled',
                    },
                  ]}
                />
              </Row>
            </Fields>
            <ResetButtonContainer>
              <Button
                title="Reset Filters"
                size="small"
                disabled={isEqual(initialValues, values)}
                type="reset"
                onClick={() => {
                  form.reset();
                  setFiltersToURL(initialValues);
                }}
              >
                Reset Filters
              </Button>
            </ResetButtonContainer>
          </FiltersForm>
        )}
      </Form>
    </FiltersPanel>
  );
};

export {Filters};
