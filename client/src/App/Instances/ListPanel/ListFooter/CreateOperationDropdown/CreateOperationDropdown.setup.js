/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export const mockUseOperationApply = {
  applyBatchOperation: jest.fn(),
};

// eslint-disable-next-line react/prop-types
export const mockConfirmOperationModal = ({bodyText}) => (
  <div data-test="mock-confirm-operation-modal">{bodyText}</div>
);
