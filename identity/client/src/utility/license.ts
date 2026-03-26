/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { checkLicense } from "src/utility/api/headers";
import { useApi } from "src/utility/api";
import type { License } from "@camunda/camunda-api-zod-schemas/8.10";

export function useLicense(): License | null {
  const { data } = useApi(checkLicense);
  return data;
}
