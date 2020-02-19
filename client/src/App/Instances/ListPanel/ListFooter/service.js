/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const getMaxPage = (total, perPage) => Math.ceil(total / perPage);

export const isPaginationRequired = (maxPage, total) => {
  return !(maxPage === 1 || total === 0);
};
