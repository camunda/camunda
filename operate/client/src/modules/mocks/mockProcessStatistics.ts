/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockProcessStatistics = [
  {
    activityId: 'userTask',
    active: 1,
    canceled: 2,
    incidents: 0,
    completed: 0,
  },
  {
    activityId: 'EndEvent_0crvjrk',
    active: 0,
    canceled: 4,
    incidents: 3,
    completed: 0,
  },
];

const mockProcessStatisticsWithFinished = [
  ...mockProcessStatistics,
  {
    activityId: 'serviceTask',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 10,
  },
  {
    activityId: 'endEvent2',
    active: 0,
    canceled: 4,
    incidents: 0,
    completed: 0,
  },
];

const mockProcessStatisticsWithActiveAndIncidents = [
  {
    activityId: 'userTask',
    active: 1,
    canceled: 2,
    incidents: 3,
    completed: 4,
  },
  {
    activityId: 'endEvent',
    active: 5,
    canceled: 0,
    incidents: 3,
    completed: 0,
  },
  {
    activityId: 'startEvent',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 0,
  },
];

export {
  mockProcessStatistics,
  mockProcessStatisticsWithFinished,
  mockProcessStatisticsWithActiveAndIncidents,
};
