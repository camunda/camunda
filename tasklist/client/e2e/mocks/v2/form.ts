/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Form} from '@vzeta/camunda-api-zod-schemas/tasklist';
import schema from '@/resources/bigForm.json' assert {type: 'json'};

const mockForm = (customFields: Partial<Form> = {}): Form => ({
  tenantId: '<default>',
  schema: JSON.stringify(schema),
  version: 1,
  formKey: '1234',
  bpmnId: 'bigForm',
  ...customFields,
});

export {mockForm};
