/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockBatchStatus = [
  {completedOperationsCount: 1, failedOperationsCount: 0, instancesCount: 1}, //delete instance
  {completedOperationsCount: 3, failedOperationsCount: 7, instancesCount: 10}, //both
  {completedOperationsCount: 0, failedOperationsCount: 10, instancesCount: 10}, //all fail
  {completedOperationsCount: 10, failedOperationsCount: 0, instancesCount: 10}, //all success
  {completedOperationsCount: 3, failedOperationsCount: 7, instancesCount: 10}, //both
  {completedOperationsCount: 0, failedOperationsCount: 1, instancesCount: 1}, // single fail
  {completedOperationsCount: 1, failedOperationsCount: 0, instancesCount: 1}, //single success
];

export {mockBatchStatus};
