/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Description, Grid, Title} from './styled';
import {Link, Stack} from '@carbon/react';

type Props = {
  heading: string;
  description: string;
  icon: React.ReactNode;
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
  link,
  className,
}) => {
  return (
    <Grid className={className}>
      <div>{icon}</div>
      <Stack gap={3}>
        <Title>{heading}</Title>
        <Description>{description}</Description>
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
