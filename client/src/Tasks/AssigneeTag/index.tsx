/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CurrentUser} from 'modules/types';
import {Tag} from './styled';

import {CircleDash, UserAvatar, UserAvatarFilled} from '@carbon/icons-react';

type Props = {
  currentUser: CurrentUser;
  assignee: string | null;
  isShortFormat?: boolean;
};

const AssigneeTag: React.FC<Props> = ({
  currentUser,
  assignee,
  isShortFormat = true,
}) => {
  const {userId} = currentUser;
  const isAssigned = assignee !== null;
  const isAssignedToCurrentUser = assignee === userId;

  if (!isAssigned) {
    return (
      <Tag title="Task unassigned" unselectable="off">
        <CircleDash size={16} />
        Unassigned
      </Tag>
    );
  }
  if (isAssignedToCurrentUser) {
    return (
      <Tag
        $isHighlighted
        $isAssigned
        title="Task assigned to me"
        unselectable="off"
      >
        <UserAvatarFilled size={16} />
        {isShortFormat ? 'Me' : 'Assigned to me'}
      </Tag>
    );
  }
  return (
    <Tag $isAssigned title={`Task assigned to ${assignee}`} unselectable="off">
      <UserAvatar size={16} />
      {isShortFormat ? assignee : `Assigned to ${assignee}`}
    </Tag>
  );
};

export {AssigneeTag};
