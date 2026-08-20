/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function getStage(host: string): "dev" | "int" | "prod" | "unknown" {
  if (host.includes(import.meta.env.VITE_DEV_ENV_URL)) {
    return "dev";
  }

  if (host.includes(import.meta.env.VITE_INT_ENV_URL)) {
    return "int";
  }

  if (host.includes(import.meta.env.VITE_PROD_ENV_URL)) {
    return "prod";
  }

  return "unknown";
}

export { getStage };
