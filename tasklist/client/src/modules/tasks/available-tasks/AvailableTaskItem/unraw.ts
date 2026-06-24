/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function unraw(str: string) {
  return str.replace(
    /\\([bfnrtv0])|\\x([0-9a-f]{2})|\\u([0-9a-f]{4})|\\u\{([0-9a-f]+)\}|\\(.)/gi,
    (match, specials, xcode, ucode, ucodeLong, other) => {
      if (specials !== undefined) {
        if (specials === 'b') {
          return '\b';
        } else if (specials === 'f') {
          return '\f';
        } else if (specials === 'n') {
          return '\n';
        } else if (specials === 'r') {
          return '\r';
        } else if (specials === 't') {
          return '\t';
        } else if (specials === 'v') {
          return '\v';
        } else if (specials === '0') {
          return '\0';
        }
        return specials;
      } else if (xcode !== undefined) {
        return String.fromCharCode(parseInt(xcode, 16));
      } else if (ucode !== undefined) {
        return String.fromCharCode(parseInt(ucode, 16));
      } else if (ucodeLong !== undefined) {
        return String.fromCharCode(parseInt(ucodeLong, 16));
      } else if (other !== undefined) {
        return other;
      } else {
        return match;
      }
    },
  );
}

export {unraw};
