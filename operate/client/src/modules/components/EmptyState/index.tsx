/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Description, Grid, Title} from './styled';
import {Button, Link, Stack} from '@carbon/react';

type Props = {
  heading: string;
  description: string;
  icon: React.ReactNode;
  button?: {
    label: string;
    href?: string;
    onClick?: () => void;
  };
  link?: {
    label: string;
    href: string;
    onClick?: () => void;
  };
  className?: string;
};

const EmptyState: React.FC<Props> = ({
  heading,
  description,
  icon,
  button,
  link,
  className,
}) => {
  return (
    <Grid className={className}>
      <div>{icon}</div>
      <Stack gap={3}>
        <Title>{heading}</Title>
        <Description>{description}</Description>
        {button !== undefined && (
          <Link href={button.href} target="_blank" rel="noreferrer">
            <Button
              kind="primary"
              title={button.label}
              size="md"
              onClick={button.onClick}
            >
              {button.label}
            </Button>
          </Link>
        )}
        {link !== undefined && (
          <Link href={link.href} onClick={link.onClick} target="_blank">
            {link.label}
          </Link>
        )}
      </Stack>
    </Grid>
  );
};

export {EmptyState};
