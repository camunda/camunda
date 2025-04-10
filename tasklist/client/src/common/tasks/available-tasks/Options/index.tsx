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
import {autoSelectNextTaskStore} from 'common/tasks/next-task/autoSelectFirstTask';
import styles from './styles.module.scss';

type Props = {
  onAutoSelectToggle?: (state: boolean) => void;
};
const Options: React.FC<Props> = observer(({onAutoSelectToggle}) => {
  const {t} = useTranslation();

  return (
    <section
      className={styles.container}
      aria-label={t('taskOptionsSectionAria')}
    >
      <Toggle
        id="toggle-auto-select-task"
        data-testid="toggle-auto-select-task"
        size="sm"
        labelText={t('taskOptionsAutoSelectLabel')}
        aria-label={t('taskOptionsAutoSelectLabel')}
        hideLabel
        labelA={t('taskOptionsAutoSelectOffAria')}
        labelB={t('taskOptionsAutoSelectOnAria')}
        toggled={autoSelectNextTaskStore.enabled}
        onToggle={(state) => {
          if (state) {
            autoSelectNextTaskStore.enable();
          } else {
            autoSelectNextTaskStore.disable();
          }
          onAutoSelectToggle?.(state);
        }}
      />
    </section>
  );
});

export {Options};
