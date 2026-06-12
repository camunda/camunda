/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {Dropdown, Layer, type OnChangeData} from '@carbon/react';
import {languageItems, type SelectionOption} from 'modules/i18n';
import styles from '../styles.module.scss';

export const LanguageSelector: React.FC = observer(() => {
  const {i18n, t} = useTranslation();

  const [selectedLanguage, setSelectedLanguage] = useState(
    i18n.resolvedLanguage ?? 'en',
  );

  useEffect(() => {
    if (selectedLanguage !== i18n.language) {
      i18n.changeLanguage(selectedLanguage);
      localStorage.setItem('language', selectedLanguage);
    }
  }, [selectedLanguage, i18n]);

  const handleLanguageChange = (e: OnChangeData<SelectionOption>) => {
    setSelectedLanguage(e.selectedItem?.id ?? 'en');
  };

  return (
    <Layer>
      <div className={styles.languageDropdownPadding}>
        <Dropdown
          id="language-dropdown"
          label={t('languageSelectorLabel')}
          titleText={t('languageSelectorTitle')}
          items={languageItems}
          itemToString={(item) => (item ? item.label : '')}
          onChange={handleLanguageChange}
          selectedItem={languageItems.find(
            (item) => item.id === selectedLanguage,
          )}
        />
      </div>
    </Layer>
  );
});
