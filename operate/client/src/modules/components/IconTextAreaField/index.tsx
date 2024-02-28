/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {useFieldError} from 'modules/hooks/useFieldError';
import {IconTextArea} from '../IconInput';

type Props = React.ComponentProps<typeof IconTextArea> & {
  name: string;
};

const IconTextAreaField: React.FC<Props> = ({name, ...props}) => {
  const error = useFieldError(name);

  return (
    <IconTextArea
      {...props}
      invalid={error !== undefined}
      invalidText={error}
    />
  );
};
export {IconTextAreaField};
