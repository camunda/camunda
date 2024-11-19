/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import bigFormSchema from '@/resources/bigForm.json' assert {type: 'json'};
import subscribeFormSchema from '@/resources/subscribeForm.json' assert {type: 'json'};

const bigForm = {
  id: 'userTaskForm_3j0n396',
  processDefinitionKey: '2251799813685255',
  schema: JSON.stringify(bigFormSchema),
};

const subscribeForm = {
  id: 'foo',
  processDefinitionKey: '2251799813685255',
  schema: JSON.stringify(subscribeFormSchema),
  title: 'Subscribe',
};

const invalidForm = {
  id: 'foo',
  processDefinitionKey: '2251799813685255',
  schema: `${JSON.stringify(subscribeFormSchema)}invalidschema`,
  title: 'Subscribe',
};

export {bigForm, subscribeForm, invalidForm};
