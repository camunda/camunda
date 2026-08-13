/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';

import {currentTheme} from 'modules/stores/currentTheme';
import {Theme} from '@carbon/react';

type Props = {
  children?: React.ReactNode;
};

const ThemeProvider = observer<React.FC<Props>>(({children}) => {
  const carbonTheme = currentTheme.theme === 'light' ? 'g10' : 'g100';

  // Carbon's <Theme> only sets data-carbon-theme on its own local wrapper,
  // not on <html>. @camunda/design-system's C4Provider reads it from
  // document.documentElement specifically (its own documented Carbon-
  // coexistence mechanism), so the host has to mirror it there for DS
  // components to track Carbon's theme without a separate toggle.
  useEffect(() => {
    document.documentElement.setAttribute('data-carbon-theme', carbonTheme);
    return () => {
      document.documentElement.removeAttribute('data-carbon-theme');
    };
  }, [carbonTheme]);

  return (
    <Theme theme={carbonTheme} className="carbonThemeProvider">
      {children}
    </Theme>
  );
});

export {ThemeProvider};
