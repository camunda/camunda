/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as React from 'react';
import {Base, Header, Body, Footer} from './styled';

interface Props {
  children: React.ReactNode;
  title: string;
  footer?: string;
  className?: string;
  hasTransparentBackground?: boolean;
  Icon?: React.ReactElement;
}

const Panel: React.FC<Props> = ({
  children,
  title,
  footer,
  className,
  hasTransparentBackground,
  Icon,
}) => {
  const hasFooter = footer !== undefined;

  return (
    <Base className={className} hasFooter={hasFooter}>
      {title !== undefined && (
        <Header>
          {title}
          {Icon}
        </Header>
      )}
      <Body hasTransparentBackground={hasTransparentBackground}>
        {children}
      </Body>
      {hasFooter && <Footer>{footer}</Footer>}
    </Base>
  );
};

export {Panel};
