/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {TextInput} from '@carbon/react';
import {useFieldError} from 'modules/hooks/useFieldError';

type Props = React.ComponentProps<typeof TextInput> & {
  name: string;
};

const TextInputField: React.FC<Props> = ({name, ...props}) => {
  const error = useFieldError(name);

  return (
    <TextInput {...props} invalid={error !== undefined} invalidText={error} />
  );
};
export {TextInputField};
