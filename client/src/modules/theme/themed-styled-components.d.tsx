/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Union} from 'ts-toolbelt';
import {themes} from './themes';

type CustomTheme = Union.Merge<typeof themes.g10 | typeof themes.g100>;

declare module 'styled-components' {
  export interface DefaultTheme extends CustomTheme {}
}
