/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {SORT_ORDER} from 'modules/constants';
const SECONDARY_SORT_KEY = 'id';

function sanitize(value) {
  return Number.isInteger(value) ? value : value.toLowerCase();
}

export function sortData(data, key, order) {
  const modifier = order === SORT_ORDER.DESC ? -1 : 1;

  function compare(a, b) {
    // we want empty values to come last
    if (!a[key]) return 1;
    if (!b[key]) return -1;

    const valA = sanitize(a[key]);
    const valB = sanitize(b[key]);

    const comparison =
      key === 'creationTime'
        ? new Date(a[key]) > new Date(b[key])
        : valA > valB;

    // this will sort entries with same value by secondary sort key
    if (valA === valB)
      return a[SECONDARY_SORT_KEY] > b[SECONDARY_SORT_KEY] ? 1 : -1;

    return (comparison ? 1 : -1) * modifier;
  }

  let arr = data.slice(0);
  arr.sort(compare);

  return arr;
}
