/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Form} from '@camunda/camunda-api-zod-schemas/8.9';
import * as schemas from 'common/mocks/form-schema';

const form: Form = {
  formKey: 'form-0',
  schema: schemas.basicInputForm,
  version: 1,
  tenantId: '<default>',
};

const invalidForm: Form = {
  formKey: 'form-0',
  schema: schemas.invalidForm,
  version: 1,
  tenantId: '<default>',
};

const dynamicForm: Form = {
  formKey: 'form-0',
  version: 1,
  schema: schemas.dynamicRadioOptions,
  tenantId: '<default>',
};

const nestedForm: Form = {
  formKey: 'form-0',
  version: 1,
  schema: schemas.nestedForm,
  tenantId: '<default>',
};

const noInputForm: Form = {
  formKey: 'form-0',
  version: 1,
  schema: schemas.noInput,
  tenantId: '<default>',
};

const formWithDocumentPreview: Form = {
  formKey: 'form-0',
  schema: schemas.documentPreview,
  version: 1,
  tenantId: '<default>',
};

export {
  form,
  invalidForm,
  dynamicForm,
  nestedForm,
  noInputForm,
  formWithDocumentPreview,
};
