/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CheckmarkFilled, ErrorFilled} from '@carbon/react/icons';
import type {AuditLogResult} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import styles from './AuditLogItemStatusIcon.module.scss';

type Props = {
  status: AuditLogResult;
};

const AuditLogItemStatusIcon: React.FC<Props> = ({status}) => {
  if (status === 'SUCCESS') {
    return (
      <CheckmarkFilled
        data-testid="success-icon"
        size={20}
        className={styles.successIcon}
      />
    );
  }

  return (
    <ErrorFilled
      data-testid="fail-icon"
      size={20}
      className={styles.failIcon}
    />
  );
};

export {AuditLogItemStatusIcon};
