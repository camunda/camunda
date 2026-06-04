/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// TODO: Replace stub with real API call once AgentDashboardService is ready:
// import {get} from 'request';
// export async function loadAgenticDashboard() {
//   const response = await get('api/dashboard/agentic-control-plane-dashboard');
//   return response.json();
// }

export async function loadAgenticDashboard() {
  return {
    availableFilters: [{type: 'processScope'}],
    tiles: [],
  };
}
