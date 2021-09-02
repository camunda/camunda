/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {User} from 'modules/types';

function getUserDisplayName(user: User) {
  if (user === null) {
    return '--';
  }

  if (user.firstname === null && user.lastname === null) {
    return user.username;
  }

  return `${user.firstname ?? ''} ${user.lastname ?? ''}`.trim();
}

export {getUserDisplayName};
