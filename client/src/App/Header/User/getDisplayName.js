/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function getDisplayName({firstname, lastname, username}) {
  if (firstname && lastname) {
    return `${firstname} ${lastname}`;
  }

  if (firstname) {
    return firstname;
  }

  if (lastname) {
    return lastname;
  }

  return username ?? '';
}

export {getDisplayName};
