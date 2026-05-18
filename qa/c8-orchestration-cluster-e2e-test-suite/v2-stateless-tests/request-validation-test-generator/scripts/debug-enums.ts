#!/usr/bin/env ts-node

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {loadSpec} from '../src/spec/loader.js';
import path from 'path';

(async () => {
  const specPath = path.resolve(process.cwd(), '../../rest-api.generated.yaml');
  const model = await loadSpec(specPath);
  for (const op of model.operations) {
    const seen: string[] = [];
    function walk(node: any, trail: string[]) {
      if (!node || typeof node !== 'object') return;
      if (Array.isArray(node.enum) && node.enum.length) {
        seen.push(trail.join('.') + ' enum[' + node.enum.length + ']');
      }
      if (node.properties) {
        for (const [k, v] of Object.entries<any>(node.properties))
          walk(v, trail.concat(k));
      }
      if (node.items) walk(node.items, trail.concat('[]'));
      if (Array.isArray(node.allOf))
        for (const part of node.allOf) walk(part, trail);
    }
    if (op.requestBodySchema) walk(op.requestBodySchema, []);
    if (seen.length) {
      console.log(
        op.operationId,
        'enums:',
        seen.slice(0, 5).join('; '),
        'total',
        seen.length,
      );
    }
  }
})();
