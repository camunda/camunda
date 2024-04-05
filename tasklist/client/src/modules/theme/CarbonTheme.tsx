/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
