/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {Base, Header, Body, Footer} from './styled';
import {css} from 'styled-components';

interface Props {
  children: React.ReactNode;
  title: string;
  footer?: string;
  className?: string;
  hasRoundTopLeftCorner?: boolean;
  hasRoundTopRightCorner?: boolean;
  hasTransparentBackground?: boolean;
  Icon?: React.ReactElement;
  extraBodyStyles?: ReturnType<typeof css>;
}

const Panel: React.FC<Props> = ({
  children,
  title,
  footer,
  className,
  hasRoundTopLeftCorner,
  hasRoundTopRightCorner,
  hasTransparentBackground,
  Icon,
  extraBodyStyles,
}) => {
  const hasFooter = footer !== undefined;

  return (
    <Base
      className={className}
      hasRoundTopLeftCorner={hasRoundTopLeftCorner}
      hasRoundTopRightCorner={hasRoundTopRightCorner}
      hasFooter={hasFooter}
    >
      {title !== undefined && (
        <Header>
          {title}
          {Icon}
        </Header>
      )}
      <Body
        hasTransparentBackground={hasTransparentBackground}
        extraBodyStyles={extraBodyStyles}
      >
        {children}
      </Body>
      {hasFooter && <Footer>{footer}</Footer>}
    </Base>
  );
};

export {Panel};
