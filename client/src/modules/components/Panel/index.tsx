/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Base, Header, Body, Footer} from './styled';

type Props = {
  children: React.ReactNode;
  title: string;
  footer?: string;
  className?: string;
  Icon?: React.ReactElement;
  variant?: 'background' | 'layer';
};

const Panel: React.FC<Props> = ({
  children,
  title,
  footer,
  className,
  Icon,
  variant = 'background',
}) => {
  const hasFooter = footer !== undefined;

  return (
    <Base className={className} $hasFooter={hasFooter} $variant={variant}>
      {title !== undefined && (
        <Header>
          {title}
          {Icon}
        </Header>
      )}
      <Body>{children}</Body>
      {hasFooter && <Footer>{footer}</Footer>}
    </Base>
  );
};

export {Panel};
