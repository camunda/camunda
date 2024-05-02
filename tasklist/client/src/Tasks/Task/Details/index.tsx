/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Section} from '@carbon/react';
import {Task, CurrentUser} from 'modules/types';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {Aside} from './Aside';
import {Header} from './Header';
import styles from './styles.module.scss';

type Props = {
  children?: React.ReactNode;
  task: Task;
  onAssignmentError: () => void;
  user: CurrentUser;
};

const Details: React.FC<Props> = ({
  children,
  onAssignmentError,
  task,
  user,
}) => {
  return (
    <div className={styles.container} data-testid="details-info">
      <Section className={styles.content} level={4}>
        <TurnOnNotificationPermission />
        <Header task={task} user={user} onAssignmentError={onAssignmentError} />
        {children}
      </Section>
      <Aside task={task} user={user} />
    </div>
  );
};

export {Details};
