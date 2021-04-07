/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from '@apollo/client';
import {Form} from 'modules/types';
import {form} from 'modules/mock-schema/mocks/form';

type FormQueryVariables = Pick<Form, 'id' | 'processDefinitionId'>;

interface GetForm {
  form: Pick<Form, 'schema'>;
}

const GET_FORM = gql`
  query GetForm($id: String!, $processDefinitionId: String!) {
    form(id: $id, processDefinitionId: $processDefinitionId) {
      schema
    }
  }
`;

const mockGetForm = {
  request: {
    query: GET_FORM,
    variables: {
      id: 'camunda-forms:bpmn:form-0',
      processDefinitionId: 'process',
    },
  },
  result: {
    data: {
      form,
    },
  },
};

export type {FormQueryVariables, GetForm};
export {GET_FORM, mockGetForm};
