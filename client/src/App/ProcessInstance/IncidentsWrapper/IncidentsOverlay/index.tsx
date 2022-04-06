/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  children?: React.ReactNode | React.ReactNode[];
};

function IncidentsOverlay(props: Props) {
  return <Styled.Overlay {...props}>{props.children}</Styled.Overlay>;
}

export default IncidentsOverlay;
