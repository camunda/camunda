/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from 'modules/components/Button';
import {Description, Grid, LinkContainer, Title, Anchor} from './styled';

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
      <div>
        <Title>{heading}</Title>
        <Description>{description}</Description>
        {button !== undefined && (
          <a href={button.href} target="_blank" rel="noreferrer">
            <Button
              color="primary"
              title={button.label}
              size="medium"
              onClick={button.onClick}
            >
              {button.label}
            </Button>
          </a>
        )}
        {link !== undefined && (
          <LinkContainer>
            <Anchor href={link.href} target="_blank" onClick={link.onClick}>
              {link.label}
            </Anchor>
          </LinkContainer>
        )}
      </div>
    </Grid>
  );
};

export {EmptyState};
