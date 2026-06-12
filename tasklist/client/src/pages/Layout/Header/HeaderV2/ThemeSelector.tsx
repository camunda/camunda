/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {C3ThemeSelector} from '@camunda/camunda-composite-components';
import {themeStore} from 'modules/theme/theme';

export const ThemeSelector: React.FC = observer(() => {
  const {selectedTheme, changeTheme} = themeStore;
  const {t} = useTranslation();
  return (
    <C3ThemeSelector
      currentTheme={selectedTheme}
      onChange={(theme) => changeTheme(theme as 'system' | 'dark' | 'light')}
      labels={{
        legend: t('themeSelectorLegend'),
        light: t('themeLight'),
        system: t('themeSystem'),
        dark: t('themeDark'),
      }}
    />
  );
});
