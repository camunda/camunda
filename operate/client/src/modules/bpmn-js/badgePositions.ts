/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MODIFICATIONS = {top: -14, right: -7};
const DECISION_STATE = {
  bottom: 12,
  left: -12,
};
const ACTIVE_BADGE = {
  bottom: 9,
  left: 0,
};
const INCIDENTS_BADGE = {
  bottom: 9,
  right: 0,
};
const CANCELED_BADGE = {
  top: -16,
  left: 0,
};

const COMPLETED_BADGE = {
  top: -16,
  right: 0,
};

const COMPLETED_END_EVENT_BADGE = {
  bottom: 1,
  left: 17,
};

const SUBPROCESS_WITH_INCIDENTS = {
  bottom: -5,
  right: -12,
};

export {
  MODIFICATIONS,
  DECISION_STATE,
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
};
