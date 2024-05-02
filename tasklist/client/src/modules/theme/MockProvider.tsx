/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
