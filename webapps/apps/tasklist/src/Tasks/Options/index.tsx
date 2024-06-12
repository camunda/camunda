/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {Toggle} from '@carbon/react';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import styles from './styles.module.scss';

type Props = {
  onAutoSelectToggle?: (state: boolean) => void;
};
const Options: React.FC<Props> = observer(({onAutoSelectToggle}) => {
  return (
    <section className={styles.container} aria-label="Options">
      <Toggle
        id="toggle-auto-select-task"
        data-testid="toggle-auto-select-task"
        size="sm"
        labelText="Auto-select first available task"
        aria-label="Auto-select first available task"
        hideLabel
        labelA="Off"
        labelB="On"
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
