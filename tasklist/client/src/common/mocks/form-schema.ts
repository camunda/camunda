/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const basicInputForm = JSON.stringify({
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
  id: 'integration_form',
});

const nestedForm = JSON.stringify({
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
});

const invalidForm = `
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
  `;

const documentPreview = JSON.stringify({
  components: [
    {
      label: 'My documents',
      type: 'documentPreview',
      id: 'myDocuments',
      dataSource: '=myDocuments',
    },
  ],
  type: 'default',
  id: 'integration_form',
});

const noInput = JSON.stringify({
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
  id: 'integration_form',
});

const dynamicRadioOptions = JSON.stringify({
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
});

export {
  invalidForm,
  nestedForm,
  documentPreview,
  noInput,
  dynamicRadioOptions,
  basicInputForm,
};
