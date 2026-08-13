/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Help} from '@carbon/react/icons';
import {Button, Heading, type ToolButtonProps} from '@camunda/design-system';
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

function InfoButton({onClick, isActive, buttonProps}: ToolButtonProps) {
  return (
    <Button
      {...buttonProps}
      type="button"
      variant="ghost"
      size="icon"
      aria-label="Info"
      onClick={onClick}
    >
      <Help aria-hidden className="size-4" />
      <span className="sr-only">{isActive ? 'Close info' : 'Open info'}</span>
    </Button>
  );
}

const InfoPanel: React.FC<{isPaidPlan: boolean}> = ({isPaidPlan}) => {
  const items = isPaidPlan
    ? [...INFO_ITEMS, FEEDBACK_AND_SUPPORT_ITEM, COMMUNITY_FORUM_ITEM]
    : [...INFO_ITEMS, COMMUNITY_FORUM_ITEM];

  return (
    <div className="h-[calc(100dvh-3rem)] border-l border-border bg-background p-4 shadow-lg">
      <Heading as="h2" variant="heading-sm">
        Info
      </Heading>
      <div className="mt-4 flex flex-col gap-1">
        {items.map(({key, label, trackingLink, href}) => (
          <Button
            key={key}
            type="button"
            variant="ghost"
            className="w-full justify-start"
            onClick={() => {
              tracking.track({eventName: 'info-bar', link: trackingLink});
              window.open(href, '_blank');
            }}
          >
            {label}
          </Button>
        ))}
      </div>
    </div>
  );
};

export {InfoButton, InfoPanel};
