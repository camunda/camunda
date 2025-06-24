/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiPost } from "src/utility/api/request";

export const SETUP_ENDPOINT = "/setup/user";

export type AdminUser = {
  username: string;
  name: string;
  email: string;
  password: string;
};

export const createAdminUser: ApiDefinition<undefined, AdminUser> = (user) =>
  apiPost(SETUP_ENDPOINT, user);
