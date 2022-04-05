/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  children?: React.ReactNode;
  scrollable?: boolean;
  onScroll?: (event: React.UIEvent<HTMLDivElement, UIEvent>) => void;
};

const PanelBody = React.forwardRef<any, Props>(function PanelBody(props, ref) {
  return <Styled.Body ref={ref} {...props} />;
});

export default PanelBody;
