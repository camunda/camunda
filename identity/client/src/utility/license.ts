/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useQuery } from "@tanstack/react-query";
import { licenseQueries } from "src/utility/api/headers/queries";
import type { License } from "@camunda/camunda-api-zod-schemas/8.10";

export function useLicense(): License | null {
  const { data } = useQuery(licenseQueries.current());
  return data ?? null;
}
