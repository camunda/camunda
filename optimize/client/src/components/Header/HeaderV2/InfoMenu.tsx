/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Help} from '@carbon/react/icons';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@camunda/design-system';

import {t} from 'translation';

interface InfoMenuProps {
  docsUrl: string;
  enterpriseMode: boolean;
}

export default function InfoMenu({docsUrl, enterpriseMode}: InfoMenuProps) {
  const items = [
    {key: 'documentation', label: t('navigation.documentation').toString(), href: docsUrl},
    {
      key: 'academy',
      label: t('navigation.academy').toString(),
      href: 'https://academy.camunda.com/',
    },
    {
      key: 'feedbackAndSupport',
      label: t('navigation.feedback').toString(),
      href: enterpriseMode
        ? 'https://jira.camunda.com/projects/SUPPORT/queues'
        : 'https://forum.camunda.io/',
    },
    {
      key: 'slackCommunityChannel',
      label: 'Slack Community Channel',
      href: 'https://camunda.com/slack',
    },
  ];

  const title = t('navigation.info').toString();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" aria-label={title}>
          <Help aria-hidden className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align="end">
        <DropdownMenuLabel>{title}</DropdownMenuLabel>
        {items.map(({key, label, href}) => (
          <DropdownMenuItem key={key} onClick={() => window.open(href, '_blank')}>
            {label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
