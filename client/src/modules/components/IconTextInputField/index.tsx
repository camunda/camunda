/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {useFieldError} from 'modules/hooks/useFieldError';
import {IconTextInput} from '../IconTextInput';

type Props = React.ComponentProps<typeof IconTextInput> & {
  name: string;
};

const IconTextInputField: React.FC<Props> = ({name, ...props}) => {
  const error = useFieldError(name);

  return (
    <IconTextInput
      {...props}
      invalid={error !== undefined}
      invalidText={error}
    />
  );
};
export {IconTextInputField};
