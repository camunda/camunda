/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactElement, ReactNode} from 'react';

export default function isReactElement(child: ReactNode): child is ReactElement {
  return !!child && typeof child === 'object' && 'type' in child;
}
