/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Loading} from '@carbon/react';
import {Container, EmptyMessage, ErrorMessage} from './styled';
import {EmptyMessage as BaseEmptyMessage} from '../EmptyMessage';

type DefaultProps = {
  children: React.ReactNode;
  status: 'error' | 'loading' | 'content';
};

type WithEmptyMessageProps = {
  children: React.ReactNode;
  status: 'error' | 'empty' | 'loading' | 'content';
  emptyMessage: React.ComponentProps<typeof BaseEmptyMessage>;
};

const DiagramShell: React.FC<DefaultProps | WithEmptyMessageProps> = ({
  children,
  status,
  ...props
}) => (
  <Container data-testid="diagram-body" tabIndex={0}>
    {(() => {
      if (status === 'content') {
        return children;
      }

      if (status === 'loading') {
        return (
          <>
            <Loading data-testid="diagram-spinner" />
            {children}
          </>
        );
      }

      if (status === 'empty' && 'emptyMessage' in props) {
        return <EmptyMessage {...props.emptyMessage} />;
      }

      if (status === 'error') {
        return <ErrorMessage />;
      }

      return null;
    })()}
  </Container>
);

export {DiagramShell};
