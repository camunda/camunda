/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { membershipMutations } from "src/utility/api/membership/mutations";
import styled from "styled-components";
import UserSearchDropdown from "src/components/form/UserSearchDropdown";
import FormModal from "src/components/modal/FormModal";
import { useNotifications } from "src/components/notifications";
import type { Group, User } from "@camunda/camunda-api-zod-schemas/8.10";

const SelectedUsers = styled.div`
  margin-top: 0;
`;

const AssignMembersModal: FC<
  UseEntityModalCustomProps<
    { groupId: Group["groupId"] },
    { assignedUsers: User[] }
  >
> = ({ entity: { groupId }, assignedUsers, onSuccess, open, onClose }) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [loadingAssignUser, setLoadingAssignUser] = useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignUser } = useMutation(
    membershipMutations.assignGroupMember(qc),
  );

  const unassignedFilter = useCallback(
    ({ username }: User) =>
      !assignedUsers.some((user) => user.username === username) &&
      !selectedUsers.some((user) => user.username === username),
    [assignedUsers, selectedUsers],
  );

  const onSelectUser = (user: User) => {
    setSelectedUsers([...selectedUsers, user]);
  };

  const onUnselectUser =
    ({ username }: User) =>
    () => {
      setSelectedUsers(
        selectedUsers.filter((user) => user.username !== username),
      );
    };

  const canSubmit = groupId && selectedUsers.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignUser(true);
    try {
      await Promise.all(
        selectedUsers.map(({ username }) =>
          callAssignUser({ username, groupId }),
        ),
      );
      if (selectedUsers.length === 1) {
        enqueueNotification({
          kind: "success",
          title: t("userAssigned"),
          subtitle: t("userAssignedSuccessfully"),
        });
      } else {
        enqueueNotification({
          kind: "success",
          title: t("usersAssigned"),
          subtitle: t("usersAssignedSuccessfully"),
        });
      }
      onSuccess();
    } catch {
      // error notification handled globally
    } finally {
      setLoadingAssignUser(false);
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedUsers([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignUser")}
      confirmLabel={t("assignUser")}
      loading={loadingAssignUser}
      loadingDescription={t("assigningUser")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>{t("searchAndAssignUserToGroup")}</p>
      {selectedUsers.length > 0 && (
        <SelectedUsers>
          {selectedUsers.map((user) => (
            <Tag
              key={user.username}
              onClose={onUnselectUser(user)}
              size="md"
              type="blue"
              filter
            >
              {user.username}
            </Tag>
          ))}
        </SelectedUsers>
      )}
      <UserSearchDropdown
        autoFocus
        placeholder={t("searchByNameOrEmail")}
        onSelect={onSelectUser}
        filter={unassignedFilter}
        errorTitle={t("usersCouldNotLoad")}
        retryLabel={t("retry")}
      />
    </FormModal>
  );
};

export default AssignMembersModal;
