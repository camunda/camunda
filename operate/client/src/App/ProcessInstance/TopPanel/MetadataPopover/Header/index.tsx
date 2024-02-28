/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, Link} from '@carbon/react';
import {Header as BaseHeader, Title, Stack} from './styled';

type Props = {
  variant?: 'default' | 'error';
  title: string;
  link?: {href: string; label: string; onClick: () => void};
  button?: {
    onClick: () => void;
    label: string;
    title: string;
  };
};

const Header: React.FC<Props> = ({
  title,
  variant = 'default',
  link,
  button,
}) => {
  return (
    <BaseHeader>
      <Title $variant={variant}>{title}</Title>
      {(button !== undefined || link !== undefined) && (
        <Stack orientation="horizontal" gap={3}>
          {link && (
            <Link href={link.href} target="_blank" onClick={link.onClick}>
              {link.label}
            </Link>
          )}

          {button !== undefined && (
            <Button
              kind="ghost"
              size="sm"
              onClick={button.onClick}
              title={button.title}
              aria-label={button.title}
            >
              {button.label}
            </Button>
          )}
        </Stack>
      )}
    </BaseHeader>
  );
};

export {Header};
