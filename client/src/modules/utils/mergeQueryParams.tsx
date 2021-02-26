/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mergeQueryParams = ({
  newParams = '',
  prevParams,
}: {
  newParams?: string;
  prevParams: string;
}) => {
  const parsedParams = new URLSearchParams(newParams);

  new URLSearchParams(prevParams).forEach((value, key) => {
    parsedParams.set(key, value);
  });

  return parsedParams.toString();
};

export {mergeQueryParams};
