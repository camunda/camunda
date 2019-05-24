/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const props = {
  validJSON: '{"firstname":"Max","lastname":"Muster","age":31}',
  arrayInitialValue:
    '[{"code":"123.135.625","name":"Laptop Lenovo ABC-001","quantity":1,"price":488.0},{"code":"111.653.365","name":"Headset Sony QWE-23","quantity":2,"price":72.0}]',
  brokenJSON: '"firstname":"Bro","lastname":"Ken","age":31}',
  mode: {EDIT: 'edit', VIEW: 'view', UNKNOWN: 'unknown'}
};

const pageMounts = {
  handleModalClose: jest.fn(),
  handleModalSave: jest.fn(),
  headline: 'Some Headline',
  initialValue: props.validJSON,
  isModalVisible: false,
  mode: props.mode.EDIT
};

const userOpensEditModal = {
  ...pageMounts,
  isModalVisible: true,
  mode: props.mode.EDIT
};

const userOpensEditModalWithBrokenJSON = {
  ...pageMounts,
  initialValue: props.brokenJSON,
  isModalVisible: true,
  mode: props.mode.EDIT
};

const userOpensModalWithUnknownMode = {
  ...userOpensEditModal,
  mode: props.mode.UNKNOWN
};

const userOpensViewModal = {
  ...pageMounts,
  isModalVisible: true,
  mode: props.mode.VIEW
};

export const testData = {
  pageMounts,
  userOpensEditModal,
  userOpensViewModal,
  userOpensModalWithUnknownMode,
  userOpensEditModalWithBrokenJSON
};
