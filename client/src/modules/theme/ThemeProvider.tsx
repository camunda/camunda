/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';

import {currentTheme} from 'modules/stores/currentTheme';
import {Theme} from '@carbon/react';

type Props = {
  children?: React.ReactNode;
};

const ThemeProvider = observer<React.FC<Props>>(({children}) => {
  return (
    <Theme
      theme={`${currentTheme.theme === 'light' ? 'g10' : 'g100'}`}
      className="carbonThemeProvider"
    >
      {children}
    </Theme>
  );
});

export {ThemeProvider};
