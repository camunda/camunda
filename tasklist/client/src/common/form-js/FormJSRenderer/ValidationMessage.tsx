/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {FormLevelErrorMessage} from './FormLevelErrorMessage';

const useBuiltMessage = (options: {
  numberOfNamedFieldsToShow?: number;
  fieldIds: string[];
  fieldLabels: string[];
}) => {
  const {t} = useTranslation();
  const {numberOfNamedFieldsToShow, fieldIds, fieldLabels} = options;
  const parts: string[] = [];
  if (fieldIds.length == 1) {
    parts.push(t('taskDetailsFormJSSingleFieldError'));
  } else {
    parts.push(
      t('taskDetailsFormJSMultipleFieldError', {count: fieldIds.length}),
    );
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

const ValidationMessage: React.FC<{
  fieldIds: string[];
  fieldLabels: string[];
}> = ({fieldIds, fieldLabels}) => {
  const readableMessage = useBuiltMessage({
    numberOfNamedFieldsToShow: 2,
    fieldIds,
    fieldLabels,
  });
  const screenReaderMessage = useBuiltMessage({fieldIds, fieldLabels});

  return (
    <FormLevelErrorMessage
      screenReaderMessage={screenReaderMessage}
      readableMessage={readableMessage}
    />
  );
};

export {ValidationMessage};
