/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { test as base } from "@playwright/test";
import { createFixture } from "./utils/createFixture";
import { LoginPage } from "./pages/LoginPage";
import { MappingRulesPage } from "./pages/MappingRulesPage";

type Fixtures = {
  loginPage: LoginPage;
  mappingRulesPage: MappingRulesPage;
};

export const test = base.extend<Fixtures>({
  loginPage: createFixture(LoginPage),
  mappingRulesPage: createFixture(MappingRulesPage),
});
