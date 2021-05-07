/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Form} from 'modules/types';

const form: Form = {
  __typename: 'Form',
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
  schema: JSON.stringify({
    components: [
      {
        key: 'myVar',
        label: 'My variable',
        type: 'textfield',
        validate: {
          required: true,
        },
      },
      {
        key: 'isCool',
        label: 'Is cool?',
        type: 'textfield',
      },
    ],
    type: 'default',
  }),
};

const invalidForm: Form = {
  __typename: 'Form',
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
  schema: `
    {
      components: [
        {
          "key": "myVar",
          "label": "My variable",
          "type": "textfield",
          "validate": {
            "required": true
          }
        },
        {
          "key": "isCool",
          "label": "Is cool?",
          "type": "textfield"
        }
      ],
      "type": "default"
    }
  `,
};

export {form, invalidForm};
