/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test as base } from "@playwright/test";
import { Login } from "./pages/Login";

type Fixture = {
  resetData: () => Promise<void>;
  loginPage: Login;
};

export const loginTest = base.extend<Fixture>({
  loginPage: async ({ page }, use) => {
    await use(new Login(page));
  },
});
