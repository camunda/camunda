/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { spacing03 } from "@carbon/elements";
import { moderate02 } from "@carbon/motion";

export const minUnit = spacing03;

export const cssSize = (units: number) => `calc(${units} * ${minUnit})`;

export const durationMs = (durationCss: string): number => {
  if (durationCss.endsWith("ms")) {
    return parseInt(durationCss, 10);
  }
  return 0;
};

export const modalFadeDurationMs = durationMs(moderate02);
