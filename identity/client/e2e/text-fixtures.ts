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
import { TenantsPage } from "./pages/TenantsPage";
import { createFixture } from "./utils/createFixture";
import { RolesPage } from "./pages/RolesPage/RolesPage";
import { RolesDetailsPage } from "./pages/RolesPage/RolesDetailsPage";
import { MappingRulesPage } from "./pages/MappingRulesPage";
import { AuthorizationsPage } from "./pages/AuthorizationsPage";

type Fixtures = {
  header: Header;
  loginPage: LoginPage;
  usersPage: UsersPage;
  groupsPage: GroupsPage;
  tenantsPage: TenantsPage;
  rolesPage: RolesPage;
  rolesDetailsPage: RolesDetailsPage;
  mappingRulesPage: MappingRulesPage;
  authorizationsPage: AuthorizationsPage;
};

export const test = base.extend<Fixtures>({
  header: createFixture(Header),
  loginPage: createFixture(LoginPage),
  usersPage: createFixture(UsersPage),
  groupsPage: createFixture(GroupsPage),
  tenantsPage: createFixture(TenantsPage),
  rolesPage: createFixture(RolesPage),
  rolesDetailsPage: createFixture(RolesDetailsPage),
  mappingRulesPage: createFixture(MappingRulesPage),
  authorizationsPage: createFixture(AuthorizationsPage),
});
