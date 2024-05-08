/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const sortOptions = (entry: {label: string}, nextEntry: {label: string}) => {
  const label = entry.label.toUpperCase();
  const nextLabel = nextEntry.label.toUpperCase();

  if (label < nextLabel) {
    return -1;
  }
  if (label > nextLabel) {
    return 1;
  }

  return 0;
};

export {sortOptions};
