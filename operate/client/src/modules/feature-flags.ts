/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const IS_VARIABLE_FILTER_V2_ENABLED = false;
const IS_CONVERSATION_HISTORY_ENABLED =
  localStorage.getItem('FEATURE_CONVERSATION_HISTORY_ENABLED') === 'true';

export {IS_VARIABLE_FILTER_V2_ENABLED, IS_CONVERSATION_HISTORY_ENABLED};
