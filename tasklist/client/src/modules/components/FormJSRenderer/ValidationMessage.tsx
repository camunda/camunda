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
import {useTranslation} from 'react-i18next';

const ValidationMessage: React.FC<{
  fieldIds: string[];
  fieldLabels: string[];
}> = ({fieldIds, fieldLabels}) => {

  const {t} = useTranslation();

  const buildMessage = (opts?: {numberOfNamedFieldsToShow?: number}) => {
    const numberOfNamedFieldsToShow = opts?.numberOfNamedFieldsToShow;
    const parts: string[] = [];
    if (fieldIds.length == 1) {
      parts.push(t('reviewOneField'));
    } else {
      parts.push(t('reviewMultipleFields', { count: fieldIds.length }));
    }
    const namedFieldsSlice =
      numberOfNamedFieldsToShow !== undefined
        ? fieldLabels.slice(0, numberOfNamedFieldsToShow)
        : fieldLabels;

    if (namedFieldsSlice.length > 0) {
      if (fieldIds.length > namedFieldsSlice.length) {
        parts.push(
          ': ',
          namedFieldsSlice.join(', '),
          `, and ${fieldIds.length - namedFieldsSlice.length} more`,
        );
      } else if (namedFieldsSlice.length > 1) {
        parts.push(
          ': ',
          namedFieldsSlice
            .slice(0, namedFieldsSlice.length - 1)
            .concat([`and ${namedFieldsSlice[namedFieldsSlice.length - 1]}`])
            .join(', '),
        );
      } else {
        parts.push(': ', namedFieldsSlice[0]);
      }
    }
    return parts.join('');
  };

  const readableMessage = buildMessage({numberOfNamedFieldsToShow: 2});
  const screenReaderMessage = buildMessage();

  return (
    <>
      <hr className={styles.hr} />
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
    </>
  );
};

export {ValidationMessage};
