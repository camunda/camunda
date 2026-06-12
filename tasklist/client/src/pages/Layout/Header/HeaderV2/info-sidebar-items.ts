/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {tracking} from 'modules/tracking';

export function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: t('headerSidebarDocumentationLink'),
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'documentation'});
        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: t('headerSidebarCamundaAcademyLink'),
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'academy'});
        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: t('headerSidebarFeedbackAndSupportLink'),
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'feedback'});
      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  } as const;
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: t('headerSidebarCommunityForumLink'),
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'forum'});
      window.open('https://forum.camunda.io', '_blank');
    },
  };

  return isPaidPlan
    ? [
        ...BASE_INFO_SIDEBAR_ITEMS,
        FEEDBACK_AND_SUPPORT_ITEM,
        COMMUNITY_FORUM_ITEM,
      ]
    : [...BASE_INFO_SIDEBAR_ITEMS, COMMUNITY_FORUM_ITEM];
}
