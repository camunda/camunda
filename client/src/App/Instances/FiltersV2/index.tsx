/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Form, Field} from 'react-final-form';
import {useHistory} from 'react-router-dom';

import {FiltersForm, Row, VariableRow} from './styled';
import {WorkflowField} from './WorkflowField';
import {WorkflowVersionField} from './WorkflowVersionField';
import {FlowNodeField} from './FlowNodeField';
import Textarea from 'modules/components/Textarea';
import {Input} from 'modules/components/Input';
import {CheckboxGroup} from './CheckboxGroup';
import Button from 'modules/components/Button';

type FieldsType =
  | 'workflow'
  | 'workflowVersion'
  | 'ids'
  | 'errorMessage'
  | 'startDate'
  | 'endDate'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValue'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled';

type FiltersType = {
  [key in FieldsType]?: string;
};

const FIELDS: FieldsType[] = [
  'workflow',
  'workflowVersion',
  'ids',
  'errorMessage',
  'startDate',
  'endDate',
  'flowNodeId',
  'variableName',
  'variableValue',
  'operationId',
  'active',
  'incidents',
  'completed',
  'canceled',
];

const BOOLEAN_FIELDS = ['active', 'incidents', 'completed', 'canceled'];

function getFilters(searchParams: string, fields: FieldsType[]): FiltersType {
  return Array.from(new URLSearchParams(searchParams)).reduce(
    (accumulator, [param, value]) => {
      if (fields.includes(param as FieldsType)) {
        return {
          ...accumulator,
          [param]: value,
        };
      }

      return accumulator;
    },
    {}
  );
}

function parseFilters(filters: FiltersType) {
  return Object.fromEntries(
    Object.entries(filters).map(([field, value]) => {
      if (BOOLEAN_FIELDS.includes(field)) {
        return [field, value === 'true' ? true : false];
      }

      return [field, value];
    })
  );
}

const Filters: React.FC = () => {
  const history = useHistory();

  function setFiltersToURL(filters: FiltersType = {}) {
    const oldParams = Object.fromEntries(
      new URLSearchParams(history.location.search)
    );
    const fieldsToDelete = FIELDS.filter(
      (field) => filters[field] === undefined
    );
    const newParams = new URLSearchParams(
      Object.entries({
        ...oldParams,
        ...filters,
      }) as [string, string][]
    );

    fieldsToDelete.forEach((field) => {
      if (newParams.has(field)) {
        newParams.delete(field);
      }
    });

    history.push({
      ...history.location,
      search: newParams.toString(),
    });
  }

  return (
    <Form<FiltersType>
      onSubmit={setFiltersToURL}
      initialValues={parseFilters(getFilters(history.location.search, FIELDS))}
    >
      {({handleSubmit, pristine, form}) => (
        <FiltersForm onSubmit={handleSubmit}>
          <Row>
            <WorkflowField />
          </Row>
          <Row>
            <WorkflowVersionField />
          </Row>
          <Row>
            <Field name="ids">
              {({input}) => (
                <Textarea
                  {...input}
                  placeholder="Instance Id(s) separated by space or comma"
                />
              )}
            </Field>
          </Row>
          <Row>
            <Field name="errorMessage">
              {({input}) => <Input {...input} placeholder="Error Message" />}
            </Field>
          </Row>
          <Row>
            <Field name="startDate">
              {({input}) => (
                <Input
                  {...input}
                  placeholder="Start Date YYYY-MM-DD hh:mm:ss"
                />
              )}
            </Field>
          </Row>
          <Row>
            <Field name="endDate">
              {({input}) => (
                <Input {...input} placeholder="End Date YYYY-MM-DD hh:mm:ss" />
              )}
            </Field>
          </Row>
          <Row>
            <FlowNodeField />
          </Row>
          <VariableRow>
            <Field name="variableName">
              {({input}) => <Input {...input} placeholder="Variable" />}
            </Field>
            <Field name="variableValue">
              {({input}) => <Input {...input} placeholder="Value" />}
            </Field>
          </VariableRow>
          <Row>
            <Field name="operationId">
              {({input}) => <Input {...input} placeholder="Operation Id" />}
            </Field>
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
          <Row>
            <button type="submit">Filter</button>
            <Button
              title="Reset filters"
              size="small"
              disabled={pristine}
              type="reset"
              onClick={() => {
                form.reset();
                setFiltersToURL();
              }}
            >
              Reset Filters
            </Button>
          </Row>
        </FiltersForm>
      )}
    </Form>
  );
};

export {Filters};
