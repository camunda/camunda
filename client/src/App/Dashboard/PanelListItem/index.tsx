/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';

import React from 'react';

type Props = {
  children?: React.ReactNode;
};

export default function PanelListItem({children, ...props}: Props) {
  // @ts-expect-error ts-migrate(2769) FIXME: Property 'to' is missing in type '{ children: stri... Remove this comment to see the full error message
  return <Styled.PanelListItem {...props}>{children}</Styled.PanelListItem>;
}
