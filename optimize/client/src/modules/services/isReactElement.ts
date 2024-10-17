/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactElement, ReactNode} from 'react';

export default function isReactElement<P = unknown>(child: ReactNode): child is ReactElement<P> {
  return !!child && typeof child === 'object' && 'type' in child;
}
