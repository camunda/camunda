/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  messagePosition?: 'top' | 'center';
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
        return (
          <EmptyMessage
            $position={props.messagePosition}
            {...props.emptyMessage}
          />
        );
      }

      if (status === 'error') {
        const position =
          'messagePosition' in props ? props.messagePosition : 'top';
        return <ErrorMessage $position={position} />;
      }

      return null;
    })()}
  </Container>
);

export {DiagramShell};
