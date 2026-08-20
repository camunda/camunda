/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
