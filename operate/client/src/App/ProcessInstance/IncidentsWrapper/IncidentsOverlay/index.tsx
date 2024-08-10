/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Overlay} from './styled';

type Props = {
  children?: React.ReactNode | React.ReactNode[];
};

const IncidentsOverlay: React.FC<Props> = (props) => {
  return <Overlay {...props}>{props.children}</Overlay>;
};

export {IncidentsOverlay};
