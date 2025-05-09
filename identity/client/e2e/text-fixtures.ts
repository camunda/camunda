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
import { UsersPage } from "./pages/UsersPage";
import { GroupsPage } from "./pages/GroupsPage";
import { createFixture } from "./utils/createFixture";
import { RolesPage } from "./pages/RolesPage";

type Fixtures = {
  header: Header;
  loginPage: LoginPage;
  usersPage: UsersPage;
  groupsPage: GroupsPage;
  rolesPage: RolesPage;
};

export const test = base.extend<Fixtures>({
  header: createFixture(Header),
  loginPage: createFixture(LoginPage),
  usersPage: createFixture(UsersPage),
  groupsPage: createFixture(GroupsPage),
  rolesPage: createFixture(RolesPage),
});
