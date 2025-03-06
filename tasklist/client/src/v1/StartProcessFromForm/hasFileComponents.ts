/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as z from 'zod';

const partialFormSchema = z.object({
  components: z.array(
    z.object({
      type: z.string(),
      components: z.array(z.unknown()).optional(),
    }),
  ),
});

function hasFileComponents(schema: object): boolean {
  const parsedSchema = partialFormSchema.safeParse(schema);
  if (!parsedSchema.success) {
    return false;
  }

  return parsedSchema.data.components.some((component) => {
    if (['filepicker', 'documentPreview'].includes(component.type)) {
      return true;
    }

    if (['group', 'dynamiclist'].includes(component.type)) {
      return hasFileComponents(component);
    }

    return false;
  });
}

export {hasFileComponents};
