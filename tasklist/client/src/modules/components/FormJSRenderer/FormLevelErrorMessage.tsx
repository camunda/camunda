/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {Warning} from '@carbon/react/icons';
import styles from './styles.module.scss';

type Props = {
  screenReaderMessage?: string;
  readableMessage: string;
};

const FormLevelErrorMessage: React.FC<Props> = ({
  screenReaderMessage,
  readableMessage,
}) => {
  return (
    <Stack
      orientation="horizontal"
      gap={3}
      className={styles.validationMessage}
      role="alert"
      aria-label={screenReaderMessage}
    >
      <Warning aria-hidden className={styles.warningFilled} />
      <div aria-hidden>{readableMessage}</div>
    </Stack>
  );
};

export {FormLevelErrorMessage};
