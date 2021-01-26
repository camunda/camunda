/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Form, Field} from 'react-final-form';
import {useHistory} from 'react-router-dom';

import {FiltersForm, Row} from './styled';
import {WorkflowField} from './WorkflowField';
import {WorkflowVersionField} from './WorkflowVersionField';
import {FlowNodeField} from './FlowNodeField';

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

  function setFiltersToURL(filters: FiltersType) {
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
      onSubmit={(values) => {
        setFiltersToURL(values);
      }}
      initialValues={parseFilters(getFilters(history.location.search, FIELDS))}
    >
      {({handleSubmit}) => (
        <FiltersForm onSubmit={handleSubmit}>
          <Row>
            <WorkflowField />
          </Row>
          <Row>
            <WorkflowVersionField />
          </Row>
          <Row>
            <Field name="ids" component="textarea" />
          </Row>
          <Row>
            <Field
              name="errorMessage"
              component="input"
              type="text"
              placeholder="Error Message"
            />
          </Row>
          <Row>
            <Field
              name="startDate"
              component="input"
              type="text"
              placeholder="Start Date YYYY-MM-DD hh:mm:ss"
            />
          </Row>
          <Row>
            <Field
              name="endDate"
              component="input"
              type="text"
              placeholder="End Date YYYY-MM-DD hh:mm:ss"
            />
          </Row>
          <Row>
            <FlowNodeField />
          </Row>
          <Row>
            <Field
              name="variableName"
              component="input"
              type="text"
              placeholder="Variable"
            />
            <Field
              name="variableValue"
              component="input"
              type="text"
              placeholder="Value"
            />
          </Row>
          <Row>
            <Field
              name="operationId"
              component="input"
              type="text"
              placeholder="Operation Id"
            />
          </Row>
          <Row>
            <label>
              <Field name="active" component="input" type="checkbox" />
              Active
            </label>
          </Row>
          <Row>
            <label>
              <Field name="incidents" component="input" type="checkbox" />
              Incidents
            </label>
          </Row>
          <Row>
            <label>
              <Field name="completed" component="input" type="checkbox" />
              Completed
            </label>
          </Row>
          <Row>
            <label>
              <Field name="canceled" component="input" type="checkbox" />
              Canceled
            </label>
          </Row>
          <Row>
            <button type="submit">Filter</button>
            <button
              type="reset"
              onClick={() => {
                setFiltersToURL({});
              }}
            >
              Reset Filters
            </button>
          </Row>
        </FiltersForm>
      )}
    </Form>
  );
};

export {Filters};
