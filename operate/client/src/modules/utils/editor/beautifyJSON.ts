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

    // Strip the synthesized closing tokens (} / ]) character-by-character from
    // the end of the pretty-printed output, skipping over whitespace between
    // them. This avoids the line-based approach dropping meaningful content
    // when closing brackets appear inline (e.g. `"a": []`).
    let pos = pretty.length;
    for (let i = 0; i < collectionDepth; i++) {
      while (pos > 0 && /\s/.test(pretty[pos - 1])) {
        pos--;
      }
      if (pos > 0 && (pretty[pos - 1] === ']' || pretty[pos - 1] === '}')) {
        pos--;
      } else {
        break;
      }
    }
    return pretty.slice(0, pos);
  } catch {
    return value;
  }
}

export {beautifyJSON, beautifyTruncatedJSON};
