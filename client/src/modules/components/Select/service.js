/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

export function ignoreFragments(children) {
  if (isFragment(children)) {
    return ignoreFragments(children.props.children);
  }

  return React.Children.toArray(children).reduce((arr, child) => {
    if (isFragment(child)) {
      return arr.concat(child.props.children);
    }
    return [...arr, child];
  }, []);
}

function isFragment(child) {
  return child && child.type === React.Fragment;
}
