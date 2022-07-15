/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Ul} from './styled';

type Props = {
  children: React.ReactNode;
};

const OperationItems: React.FC<Props> = (props) => {
  return <Ul {...props}>{React.Children.toArray(props.children)}</Ul>;
};

export {OperationItems};
