/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'styled-components';

import {themes} from './themes';

type Props = {
  children?: React.ReactNode;
};

const MockThemeProvider: React.FC<Props> = ({children}) => {
  return <ThemeProvider theme={themes.g10}>{children}</ThemeProvider>;
};

export {MockThemeProvider};
