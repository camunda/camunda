/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Form} from 'v1/api/types';
import * as schemas from 'common/mocks/form-schema';

const form: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: schemas.basicInputForm,
  tenantId: '<default>',
  isDeleted: false,
};

const invalidForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: schemas.invalidForm,
  tenantId: '<default>',
  isDeleted: false,
};

const dynamicForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: schemas.dynamicRadioOptions,
  tenantId: '<default>',
  isDeleted: false,
};

const nestedForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: schemas.nestedForm,
  tenantId: '<default>',
  isDeleted: false,
};

const noInputForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: schemas.noInput,
  tenantId: '<default>',
  isDeleted: false,
};

const formWithDocumentPreview: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: schemas.documentPreview,
  tenantId: '<default>',
  isDeleted: false,
};

export {
  form,
  invalidForm,
  dynamicForm,
  nestedForm,
  noInputForm,
  formWithDocumentPreview,
};
