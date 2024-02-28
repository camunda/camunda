/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {storeStateLocally, getStateLocally, clearStateLocally} from '.';

describe('localStorage', () => {
  it('should store state in localstorage', () => {
    const data = {a: 1, b: 2};

    storeStateLocally(data);

    expect(getStateLocally()).toEqual(data);
  });

  it('should retrieve localstorage state', () => {
    storeStateLocally({a: 1, b: 2});
    expect(getStateLocally()).toEqual({a: 1, b: 2});
  });

  it('should clear localstorage state', () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockClear' does not exist on type '(key:... Remove this comment to see the full error message
    localStorage.removeItem.mockClear();

    clearStateLocally();

    expect(localStorage.removeItem).toHaveBeenCalledWith('sharedState');
  });
});
