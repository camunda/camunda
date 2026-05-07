/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ClassValue, clsx} from 'clsx';
import {twMerge} from 'tailwind-merge';

declare const process:
  | {env: {NODE_ENV?: string} | undefined}
  | undefined;

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function warnDroppedProps(
  component: string,
  dropped: Record<string, unknown>,
) {
  if (
    typeof process !== 'undefined' &&
    process?.env?.['NODE_ENV'] === 'production'
  )
    return;
  const keys = Object.keys(dropped).filter((k) => dropped[k] !== undefined);
  if (keys.length === 0) return;
  console.warn(
    `[@camunda/design-system] <${component}>: Carbon prop(s) ${keys
      .map((k) => `\`${k}\``)
      .join(', ')} have no shadcn equivalent and were dropped.`,
  );
}
