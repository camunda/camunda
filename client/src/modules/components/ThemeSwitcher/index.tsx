/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useTheme} from '@carbon/react';
import {useEffect} from 'react';

const ThemeSwitcher: React.FC = () => {
  const {theme} = useTheme();

  useEffect(() => {
    if (theme === 'g100') {
      document.body.classList.remove(`cds--g10`);
      document.body.classList.add(`cds--g100`);
    } else {
      document.body.classList.remove(`cds--g100`);
      document.body.classList.add(`cds--g10`);
    }
  }, [theme]);

  return null;
};

export {ThemeSwitcher};
