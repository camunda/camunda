/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {untruncateJson} from './untruncateJSON';

function beautifyJSON(value: string) {
  try {
    const parsedValue = JSON.parse(value);

    return JSON.stringify(parsedValue, null, '\t');
  } catch {
    return value;
  }
}

function beautifyTruncatedJSON(value: string) {
  try {
    const {completed, collectionDepth} = untruncateJson(value);
    const parsedValue = JSON.parse(completed);
    const pretty = JSON.stringify(parsedValue, null, '\t');

    if (collectionDepth === 0) {
      return pretty;
    }

    // Walk backwards through the pretty-printed output, skipping the last
    // `collectionDepth` closing lines (the `}` / `]` that were synthesized
    // by untruncateJson to make the JSON parseable).
    let pos = pretty.length;
    for (let i = 0; i < collectionDepth; i++) {
      const newlinePos = pretty.lastIndexOf('\n', pos - 1);
      if (newlinePos === -1) {
        break;
      }
      pos = newlinePos;
    }
    return pretty.slice(0, pos);
  } catch {
    return value;
  }
}

export {beautifyJSON, beautifyTruncatedJSON};
