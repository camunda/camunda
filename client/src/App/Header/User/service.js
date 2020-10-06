/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const getUserName = (user) => {
  if (!user.firstname && !user.lastname) {
    return user.username ?? '';
  } else if (user.firstname && user.lastname) {
    return `${user.firstname} ${user.lastname}`;
  } else {
    return `${user.firstname ?? ''}${user.lastname ?? ''}`;
  }
};

export {getUserName};
