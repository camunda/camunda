/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render as baseRender} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const user = userEvent.setup({delay: null});
function render(...args: Parameters<typeof baseRender>) {
  return {
    user,
    ...baseRender(...args),
  };
}

type UserEvent = typeof user;

export * from '@testing-library/react';
export {render};
export type {UserEvent};
