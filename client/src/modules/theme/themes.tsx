/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
