/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GlobalTheme, usePrefix} from '@carbon/react';
import {observer} from 'mobx-react-lite';
import {themeStore} from 'modules/stores/theme';
import {useLayoutEffect} from 'react';
import styles from './styles.module.scss';

const THEME_TOKENS = {
  light: 'g10',
  dark: 'g100',
} as const;

type Props = {
  children: React.ReactNode;
};

const ThemeProvider: React.FC<Props> = observer(({children}) => {
  const {actualTheme} = themeStore;
  const prefix = usePrefix();

  useLayoutEffect(() => {
    document.documentElement.dataset.carbonTheme = THEME_TOKENS[actualTheme];
  }, [actualTheme, prefix]);

  return <GlobalTheme className={styles.carbonTheme}>{children}</GlobalTheme>;
});

export {ThemeProvider};
