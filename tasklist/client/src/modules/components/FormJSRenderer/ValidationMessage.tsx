/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Stack} from '@carbon/react';
import {
  HorizontalRule,
  ValidationMessageContainer,
  WarningFilled,
} from './styled';

const ValidationMessage: React.FC<{
  fieldIds: string[];
  fieldLabels: string[];
}> = ({fieldIds, fieldLabels}) => {
  const buildMessage = (opts?: {numberOfNamedFieldsToShow?: number}) => {
    const numberOfNamedFieldsToShow = opts?.numberOfNamedFieldsToShow;
    const parts: string[] = [];
    if (fieldIds.length == 1) {
      parts.push('Please review 1 field');
    } else {
      parts.push(`Please review ${fieldIds.length} fields`);
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
      <HorizontalRule />
      <Stack
        orientation="horizontal"
        gap={3}
        as={ValidationMessageContainer}
        role="alert"
        aria-label={screenReaderMessage}
      >
        <WarningFilled aria-hidden />
        <div aria-hidden>{readableMessage}</div>
      </Stack>
    </>
  );
};

export {ValidationMessage};
