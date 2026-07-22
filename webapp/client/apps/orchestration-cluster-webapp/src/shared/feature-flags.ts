/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Off by default everywhere (no env var set = false). Only opt in via a
 * local, gitignored `.env.local` — never set this in a shared env file,
 * so every future user/teammate sees the same UI until this is removed.
 */
export const featureFlags = {
	dsTasklistUI: import.meta.env['VITE_DS_TASKLIST_UI'] === 'true',
};
