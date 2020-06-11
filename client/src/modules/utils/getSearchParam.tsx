/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const getSearchParam = (param: string, params: string) => {
  const searchParams = new URLSearchParams(params);

  return searchParams.get(param);
};

export {getSearchParam};
