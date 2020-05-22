/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';

import {Overlay} from './Overlay';
import {Spinner} from './Spinner';

const LoadingOverlay: React.FC = () => (
  <Overlay>
    <Spinner />
  </Overlay>
);

export {LoadingOverlay};
