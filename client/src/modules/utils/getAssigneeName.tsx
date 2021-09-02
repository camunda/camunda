/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const getAssigneeName = (assignee: string | null) => {
  if (assignee === null || assignee === '') {
    return '--';
  }
  return assignee;
};

export {getAssigneeName};
