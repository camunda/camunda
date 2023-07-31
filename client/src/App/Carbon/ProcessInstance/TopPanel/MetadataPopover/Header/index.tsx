/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';
import {Link} from 'modules/components/Carbon/Link';
import {Header as BaseHeader, Title, Stack} from './styled';

type Props = {
  variant?: 'default' | 'error';
  title: string;
  link?: {to: string; label: string};
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
          {link !== undefined && (
            <Link to={link.to} target="_blank">
              {link.label}
            </Link>
          )}

          {button !== undefined && (
            <Button
              kind="ghost"
              size="sm"
              onClick={button.onClick}
              title={button.title}
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
