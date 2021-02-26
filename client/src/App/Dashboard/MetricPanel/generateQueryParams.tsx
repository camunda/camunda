/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const generateQueryParams = ({
  filter,
  hasFinishedInstances,
}: {
  filter: {active?: boolean; incidents?: boolean};
  hasFinishedInstances?: boolean;
}) => {
  if (hasFinishedInstances) {
    Object.assign(filter, {
      completed: true,
      canceled: true,
    });
  }

  return `?filter=${JSON.stringify(filter)}`;
};

export {generateQueryParams};
