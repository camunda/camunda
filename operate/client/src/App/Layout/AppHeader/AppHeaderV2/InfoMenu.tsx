/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CircleHelp} from 'lucide-react';
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@camunda/design-system';
import {useMemo} from 'react';
import {tracking} from 'modules/tracking';

const INFO_ITEMS = [
  {
    key: 'docs',
    label: 'Documentation',
    trackingLink: 'documentation',
    href: 'https://docs.camunda.io/',
  },
  {
    key: 'academy',
    label: 'Camunda Academy',
    trackingLink: 'academy',
    href: 'https://academy.camunda.com/',
  },
] as const;

const FEEDBACK_AND_SUPPORT_ITEM = {
  key: 'feedbackAndSupport',
  label: 'Feedback and Support',
  trackingLink: 'feedback',
  href: 'https://jira.camunda.com/projects/SUPPORT/queues',
} as const;

const COMMUNITY_FORUM_ITEM = {
  key: 'communityForum',
  label: 'Community Forum',
  trackingLink: 'forum',
  href: 'https://forum.camunda.io',
} as const;

const InfoMenu: React.FC<{isPaidPlan: boolean}> = ({isPaidPlan}) => {
  const items = useMemo(
    () =>
      isPaidPlan
        ? [...INFO_ITEMS, FEEDBACK_AND_SUPPORT_ITEM, COMMUNITY_FORUM_ITEM]
        : [...INFO_ITEMS, COMMUNITY_FORUM_ITEM],
    [isPaidPlan],
  );

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" aria-label="Info">
          <CircleHelp aria-hidden className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align="end">
        <DropdownMenuLabel>Info</DropdownMenuLabel>
        {items.map(({key, label, trackingLink, href}) => (
          <DropdownMenuItem
            key={key}
            onClick={() => {
              tracking.track({eventName: 'info-bar', link: trackingLink});
              window.open(href, '_blank');
            }}
          >
            {label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export {InfoMenu};
