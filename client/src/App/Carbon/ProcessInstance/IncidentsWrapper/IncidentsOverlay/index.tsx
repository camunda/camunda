/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
