/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export function twoNodesPropType(
  props: any,
  propName: any,
  componentName: any
) {
  const value = props[propName] || {};
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    !value.every(React.isValidElement)
  ) {
    throw new Error(
      'Invalid prop `' +
        propName +
        '` supplied to' +
        ' `' +
        componentName +
        '`. Validation failed.'
    );
  }

  return null;
}
