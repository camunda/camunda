/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Union} from 'ts-toolbelt';
import {theme} from './theme';

type Theme = Union.Merge<typeof theme.light | typeof theme.dark>;

declare module 'styled-components' {
  export interface DefaultTheme extends Theme {}
  export type ThemedInterpolationFunction<P = {}> = (
    props: P & {theme: DefaultTheme}
  ) => Interpolation<P & {theme: DefaultTheme}>;
}
