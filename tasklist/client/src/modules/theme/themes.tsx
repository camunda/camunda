/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {g10, g100, styles} from '@carbon/elements';

const THEME_TOKENS = {
  light: 'g10',
  dark: 'g100',
} as const;
const themes = {
  [THEME_TOKENS.light]: {
    ...g10,
    legal01: styles.legal01,
    legal02: styles.legal02,
  },
  [THEME_TOKENS.dark]: {
    ...g100,
    legal01: styles.legal01,
    legal02: styles.legal02,
  },
} as const;

export {themes, THEME_TOKENS};
