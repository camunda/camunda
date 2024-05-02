/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {themeStore} from 'modules/stores/theme';
import {THEME_TOKENS} from './themes';
import {GlobalTheme} from '@carbon/react';

type Props = {
  children?: React.ReactNode;
  className?: string;
};

const CarbonTheme: React.FC<Props> = observer(({children, className}) => {
  const theme = THEME_TOKENS[themeStore.actualTheme];

  return (
    <GlobalTheme theme={theme} className={className}>
      {children}
    </GlobalTheme>
  );
});

export {CarbonTheme};
