/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {Toggle} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import styles from './styles.module.scss';

type Props = {
  onAutoSelectToggle?: (state: boolean) => void;
};
const Options: React.FC<Props> = observer(({onAutoSelectToggle}) => {
  
  const {t} = useTranslation();
  
  return (
    <section className={styles.container} aria-label={t('optionsSectionAriaLabel')}>
      <Toggle
        id="toggle-auto-select-task"
        data-testid="toggle-auto-select-task"
        size="sm"
        labelText={t('autoSelectFirstAvailableTaskLabel')}
        aria-label={t('autoSelectFirstAvailableTaskLabel')}
        hideLabel
        labelA={t('toggleOffLabel')}
        labelB={t('toggleOnLabel')}
        toggled={autoSelectNextTaskStore.enabled}
        onToggle={(state) => {
          autoSelectNextTaskStore.toggle();
          onAutoSelectToggle?.(!state);
        }}
      />
    </section>
  );
});

export {Options};
