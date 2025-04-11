/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test as base } from "@playwright/test";
import { Header } from "./pages/Header";
import { LoginPage } from "./pages/LoginPage";
import { Users } from "./pages/Users";

type PlaywrightFixtures = {
  header: Header;
  loginPage: LoginPage;
  usersPage: Users;
};

export const test = base.extend<PlaywrightFixtures>({
  header: async ({ page }, use) => {
    await use(new Header(page));
  },
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
  usersPage: async ({ page }, use) => {
    await use(new Users(page));
  },
});
