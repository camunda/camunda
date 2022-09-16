/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
