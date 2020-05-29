/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

const KEY_PARAM = ':key';

const Pages = {
  Initial({useKeyParam} = {useKeyParam: false}) {
    return `/${useKeyParam ? `${KEY_PARAM}?` : ''}`;
  },
  Login: '/login',
  TaskDetails(key: string = KEY_PARAM) {
    return `/${key}`;
  },
} as const;

export {Pages};
