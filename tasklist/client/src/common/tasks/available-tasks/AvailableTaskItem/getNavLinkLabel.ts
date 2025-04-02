/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';

function getNavLinkLabel({
  displayName,
  assigneeId,
  currentUserId,
}: {
  displayName: string;
  assigneeId: string | null | undefined;
  currentUserId: string;
}) {
  const isAssigned = typeof assigneeId === 'string';
  const isAssignedToCurrentUser = assigneeId === currentUserId;
  if (isAssigned) {
    if (isAssignedToCurrentUser) {
      return t('availableTasksNavLinkAssignedToMe', {name: displayName});
    } else {
      return t('availableTasksNavLinkAssignedTask', {name: displayName});
    }
  } else {
    return t('availableTasksNavLinkUnassignedTask', {name: displayName});
  }
}

export {getNavLinkLabel};
