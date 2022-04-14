/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render as baseRender} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

function render(...args: Parameters<typeof baseRender>) {
  return {
    user: userEvent.setup({delay: null}),
    ...baseRender(...args),
  };
}

export * from '@testing-library/react';
export {render};
