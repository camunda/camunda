/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3EmptyState} from '@camunda/camunda-composite-components';
import styles from './styles.module.scss';
import {PoweredBy} from 'modules/components/PoweredBy';

type Props = React.ComponentProps<typeof C3EmptyState>;

const Message: React.FC<Props> = (props) => {
  return (
    <div className={styles.container}>
      <C3EmptyState {...props} />
      <PoweredBy className={styles.poweredBy} />
    </div>
  );
};

export {Message};
