/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form} from 'modules/types';

const form: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  schema: JSON.stringify({
    components: [
      {
        text: '# A sample text',
        type: 'text',
      },
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
      {
        key: 'button1',
        label: 'Save',
        type: 'button',
      },
    ],
    type: 'default',
  }),
};

const invalidForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
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

const dynamicForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  schema: JSON.stringify({
    components: [
      {
        values: [],
        label: 'Radio Field',
        type: 'radio',
        id: 'radio_field',
        key: 'radio_field',
        valuesKey: 'radio_field_options',
      },
    ],
    type: 'default',
    id: 'form_test',
    executionPlatform: 'Camunda Cloud',
    executionPlatformVersion: '8.0.0',
    exporter: {
      name: 'Camunda Modeler',
      version: '5.3.0-nightly.20220831',
    },
    schemaVersion: 4,
  }),
};

export {form, invalidForm, dynamicForm};
