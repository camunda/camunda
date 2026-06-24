/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TaskDetailsRow} from './TaskDetailsLayout';
import styles from './DetailsFooter.module.scss';

type Props = {
  children: React.ReactNode;
};

const DetailsFooter: React.FC<Props> = ({children}) => {
  return (
    <div className={styles.container}>
      <TaskDetailsRow className={styles.row}>{children}</TaskDetailsRow>
    </div>
  );
};

export {DetailsFooter};
