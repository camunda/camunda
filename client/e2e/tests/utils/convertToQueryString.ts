/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const convertToQueryString = (params: Record<string, string>) => {
  return new URLSearchParams(params).toString();
};

export {convertToQueryString};
