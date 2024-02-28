/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {batchModificationStore} from './batchModification';

describe('batchModificationStore', () => {
  it('should initialize, enable, disable and reset ', async () => {
    batchModificationStore.enable();
    expect(batchModificationStore.state.isEnabled).toBe(true);

    batchModificationStore.disable();
    expect(batchModificationStore.state.isEnabled).toBe(false);

    batchModificationStore.enable();
    expect(batchModificationStore.state.isEnabled).toBe(true);

    batchModificationStore.reset();
    expect(batchModificationStore.state.isEnabled).toBe(false);
  });
});
