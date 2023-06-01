/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Loading} from '@carbon/react';
import {Container, EmptyMessage} from './styled';
import {EmptyMessage as BaseEmptyMessage} from '../EmptyMessage';

type Props = {
  children: React.ReactNode;
  status: 'error' | 'empty' | 'loading' | 'content';
  emptyMessage: React.ComponentProps<typeof BaseEmptyMessage>;
  errorMessage: React.ComponentProps<typeof BaseEmptyMessage>;
};

const DiagramShell: React.FC<Props> = ({
  children,
  status,
  emptyMessage,
  errorMessage,
}) => (
  <Container>
    {(() => {
      if (status === 'content') {
        return children;
      }

      if (status === 'loading') {
        return (
          <>
            <Loading />
            {children}
          </>
        );
      }

      if (status === 'empty') {
        return <EmptyMessage {...emptyMessage} />;
      }

      if (status === 'error') {
        return <EmptyMessage {...errorMessage} />;
      }

      return null;
    })()}
  </Container>
);

export {DiagramShell};
