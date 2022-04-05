/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form} from 'modules/types';

const form: Form = {
  __typename: 'Form',
  id: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
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
