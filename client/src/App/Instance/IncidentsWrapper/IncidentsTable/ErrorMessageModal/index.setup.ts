/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockProps = {
  content: 'some modal content',
  title: 'modal title',
  isVisible: true,
  onModalClose: jest.fn(),
};

const mockHiddenModalProps = {
  ...mockProps,
  isVisible: false,
};

export {mockProps, mockHiddenModalProps};
