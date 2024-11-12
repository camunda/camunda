/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useC3Profile} from '@camunda/camunda-composite-components';
import {themeStore} from 'modules/stores/theme';
import {useEffect} from 'react';

const C3ThemePersister: React.FC = () => {
  const {theme} = useC3Profile();

  useEffect(() => {
    themeStore.changeTheme(theme);
  }, [theme]);

  return null;
};

export {C3ThemePersister};
