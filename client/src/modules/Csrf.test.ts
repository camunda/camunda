/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getToken} from './Csrf';

const CSRF_TOKEN = '374q-2qc8jm4-mcfq97h43';

describe('Csrf', () => {
  it('should return token (only item in cookie)', () => {
    // when
    const token = getToken(`OPERATE-X-CSRF-TOKEN=${CSRF_TOKEN}`);

    // then
    expect(token).toBe(CSRF_TOKEN);
  });

  it('should return token (1st of 2 items in cookie)', () => {
    // when
    const token = getToken(
      `OPERATE-X-CSRF-TOKEN=${CSRF_TOKEN}; OPERATE-SESSION=1987456796419`
    );

    // then
    expect(token).toBe(CSRF_TOKEN);
  });

  it('should return token (2nd of 2 items in cookie)', () => {
    // when
    const token = getToken(
      `OPERATE-SESSION=1987456796419; OPERATE-X-CSRF-TOKEN=${CSRF_TOKEN}`
    );

    // then
    expect(token).toBe(CSRF_TOKEN);
  });

  it('should return token (2nd of 3 items in cookie)', () => {
    // when
    const token = getToken(
      `USERNAME=Operator; OPERATE-X-CSRF-TOKEN=${CSRF_TOKEN}; OPERATE-SESSION=1987456796419`
    );

    // then
    expect(token).toBe(CSRF_TOKEN);
  });

  it('should return undefined (no token in empty cookie)', () => {
    // when
    const token = getToken('');

    // then
    expect(token).toBe(undefined);
  });

  it('should return undefined (no token in cookie)', () => {
    // when
    const token = getToken('`USERNAME=Operator; OPERATE-SESSION=1987456796419');

    // then
    expect(token).toBe(undefined);
  });
});
