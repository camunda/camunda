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
  title: 'A form',
  version: null,
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
  title: 'A form',
  version: null,
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
  title: 'A form',
  version: null,
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

const nestedForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: JSON.stringify({
    components: [
      {
        components: [
          {
            components: [
              {
                label: 'Surname',
                type: 'textfield',
                id: 'Field_0qtjl2z',
                key: 'test_field',
              },
            ],
            showOutline: false,
            type: 'group',
            id: 'Field_155wjbg',
            path: 'nested',
          },
          {
            label: 'Name',
            type: 'textfield',
            id: 'name_field',
            key: 'name',
          },
        ],
        showOutline: false,
        type: 'group',
        id: 'Field_1kot1wx',
        path: 'root',
      },
    ],
    type: 'default',
    id: 'surnameForm',
    executionPlatform: 'Camunda Cloud',
    executionPlatformVersion: '8.4.0',
    exporter: {
      name: 'Camunda Modeler',
      version: '5.18.0-nightly.20231207',
    },
    schemaVersion: 12,
  }),
};

const noInputForm: Form = {
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionKey: 'process',
  title: 'A form',
  version: null,
  schema: JSON.stringify({
    components: [
      {
        text: 'foo',
        type: 'text',
        id: 'Field_16zrrtg',
        layout: {
          row: 'Row_0m33bd6',
        },
      },
    ],
    type: 'default',
  }),
};

export {form, invalidForm, dynamicForm, nestedForm, noInputForm};
