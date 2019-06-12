/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const initialValueTypes = {
  string: 'SomeString',
  validJSON: '{"firstname":"Max","lastname":"Muster","age":31}',
  array:
    '[{"code":"123.135.625","name":"Laptop Lenovo ABC-001","quantity":1,"price":488.0},{"code":"111.653.365","name":"Headset Sony QWE-23","quantity":2,"price":72.0}]',
  brokenJSON: '"firstname":"Bro","lastname":"Ken","age":31}'
};

const props = {
  contentEditable: true,
  initialValue: initialValueTypes.string,
  handleChange: jest.fn()
};

const viewerMounts = {
  ...props,
  contentEditable: false
};

const editorMounts = {
  ...props
};

const editorMountsWithString = {
  ...props,
  initialValue: initialValueTypes.string
};

const editorMountsWithNullValue = {
  ...props,
  initialValue: ''
};

const editorMountsWithJSON = {
  ...props,
  initialValue: initialValueTypes.validJSON
};

const editorMountsWithArray = {
  ...props,
  initialValue: initialValueTypes.string
};

const editorMountsWithBrokenJSON = {
  ...props,
  initialValue: initialValueTypes.brokenJSON
};

export const testData = {
  viewerMounts,
  editorMounts,
  editorMountsWithString,
  editorMountsWithJSON,
  editorMountsWithArray,
  editorMountsWithBrokenJSON,
  editorMountsWithNullValue
};
