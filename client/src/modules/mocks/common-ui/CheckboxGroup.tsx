/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

type Props = {
  children: React.ReactNode;
};

const CheckboxGroup: React.FC<Props> = ({children}) => {
  return <div>{children}</div>;
};

export {CheckboxGroup};
