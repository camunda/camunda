/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/8.8';
import {CircleDash, UserAvatar, UserAvatarFilled} from '@carbon/react/icons';
import {Tag as BaseTag} from '@carbon/react';
import styles from './styles.module.scss';
import cn from 'classnames';

type Props = {
  currentUser: CurrentUser;
  assignee: string | null | undefined;
  isShortFormat?: boolean;
};

type AssigneeTagProps = {
  $isHighlighted?: boolean;
  $isAssigned?: boolean;
  children: React.ReactNode;
};

const Tag: React.FC<
  React.ComponentProps<typeof BaseTag> & AssigneeTagProps
> = ({
  className = '',
  children,
  $isHighlighted,
  $isAssigned,
  size,
  ...rest
}) => (
  <BaseTag
    size={size}
    {...rest}
    className={cn(className, styles.tag, {
      [styles.assigned]: $isAssigned,
      [styles.highlighted]: $isHighlighted,
      [styles.small]: size == 'sm',
    })}
  >
    {children}
  </BaseTag>
);

const AssigneeTag: React.FC<Props> = ({
  currentUser,
  assignee,
  isShortFormat = true,
}) => {
  const {t} = useTranslation();
  const {username} = currentUser;
  const isAssigned = typeof assignee === 'string';
  const isAssignedToCurrentUser = assignee === username;

  if (!isAssigned) {
    return (
      <Tag
        title={t('assigneeTagUnassignedTitle')}
        size={isShortFormat ? 'sm' : 'md'}
        unselectable="off"
      >
        <CircleDash size={16} />
        {t('assigneeTagUnassigned')}
      </Tag>
    );
  }
  if (isAssignedToCurrentUser) {
    return (
      <Tag
        $isHighlighted
        $isAssigned
        title={t('assigneeTagAssignedToMeAria')}
        size={isShortFormat ? 'sm' : 'md'}
        unselectable="off"
      >
        <UserAvatarFilled size={16} />
        {isShortFormat
          ? t('assigneeTagAssignedToMeShortForm')
          : t('assigneeTagAssignedToMe')}
      </Tag>
    );
  }
  return (
    <Tag
      $isAssigned
      title={t('assigneeTagAssignedToXAria', {assignee})}
      size={isShortFormat ? 'sm' : 'md'}
      unselectable="off"
    >
      <UserAvatar size={16} />
      {isShortFormat ? assignee : t('assigneeTagAssignedToX', {assignee})}
    </Tag>
  );
};

export {AssigneeTag};
