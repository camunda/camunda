/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  children?: React.ReactNode;
};

export default function PanelFooter(props: Props) {
  const {children} = props;
  return <Styled.Footer {...props}>{children}</Styled.Footer>;
}
