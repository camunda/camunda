/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CmButton} from '@camunda-cloud/common-ui-react';
import React from 'react';

type Props = {
  onCmPress: () => {};
  label: React.ComponentProps<typeof CmButton>['label'];
};

const Button: React.FC<Props> = ({onCmPress, label, ...restProps}) => {
  return (
    <button {...restProps} onClick={onCmPress}>
      {label}
    </button>
  );
};

export {Button};
