/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import playwright from "eslint-plugin-playwright";

export default [
  {
    ...playwright.configs["flat/recommended"],
    files: ["tests/**", "pages/**/*"],
    rules: {
      ...playwright.configs["flat/recommended"].rules,
      "testing-library/prefer-screen-queries": "off",
      "testing-library/no-await-sync-query": "off",
    },
  },
];
