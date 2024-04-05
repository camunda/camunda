/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {decodeTaskOpenedRef, encodeTaskOpenedRef} from './reftags';

describe('reftags', () => {
  it('encodes Task Opened data into a string and can decode it back to an object', () => {
    const data = {
      by: 'user',
      filter: 'all-open',
      position: 10,
      sorting: 'completion',
    } as const;
    const encode = encodeTaskOpenedRef(data);
    const decoded = decodeTaskOpenedRef(encode);
    expect(decoded).toEqual(data);
  });

  it('returns a null when decoding null', () => {
    const decoded = decodeTaskOpenedRef(null);
    expect(decoded).toBeNull();
  });

  it('returns a null when decoding garbage data', () => {
    const decoded = decodeTaskOpenedRef('garbage that is not base 64 json');
    expect(decoded).toBeNull();
  });

  it('returns a null when decoding data that does not conform to the schema', () => {
    const base64 = btoa(JSON.stringify({this: {is: 'garbage'}}));
    const decoded = decodeTaskOpenedRef(base64);
    expect(decoded).toBeNull();
  });
});
