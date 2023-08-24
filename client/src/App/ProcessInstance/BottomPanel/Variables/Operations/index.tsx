/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, InlineLoading} from './styled';

type Props = {
  className?: string;
  showLoadingIndicator?: boolean;
  children?: React.ReactNode;
};

const Operations: React.FC<Props> = ({
  className,
  showLoadingIndicator,
  children,
}) => {
  return (
    <Container className={className}>
      {showLoadingIndicator && (
        <InlineLoading data-testid="variable-operation-spinner" />
      )}
      {children}
    </Container>
  );
};

export {Operations};
