/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { assignRoleMember } from "src/utility/api/membership";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api/hooks";
import { searchUser, User } from "src/utility/api/users";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { Role } from "src/utility/api/roles";

const SelectedUsers = styled.div`
  margin-top: 0;
`;

const AssignMembersModal: FC<
  UseEntityModalCustomProps<{ id: Role["roleKey"] }, { assignedUsers: User[] }>
> = ({ entity: role, assignedUsers, onSuccess, open, onClose }) => {
  const { t } = useTranslate("roles");
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [loadingAssignUser, setLoadingAssignUser] = useState(false);

  const {
    data: userSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchUser);

  const [callAssignUser] = useApiCall(assignRoleMember);

  const unassignedUsers =
    userSearchResults?.items.filter(
      ({ username }) =>
        !assignedUsers.some((user) => user.username === username) &&
        !selectedUsers.some((user) => user.username === username),
    ) || [];

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

  const canSubmit = role && selectedUsers.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignUser(true);

    const results = await Promise.all(
      selectedUsers.map(({ username }) =>
        callAssignUser({ username, roleId: role.id }),
      ),
    );

    setLoadingAssignUser(false);

    if (results.every(({ success }) => success)) {
      onSuccess();
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
      <p>{t("searchAndAssignUserToRole")}</p>
      {selectedUsers.length > 0 && (
        <SelectedUsers>
          {selectedUsers.map((user) => (
            <Tag
              key={user.id}
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
      <DropdownSearch
        autoFocus
        items={unassignedUsers}
        itemTitle={({ username }) => username}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByUsernameOrName")}
        onSelect={onSelectUser}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("usersCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </FormModal>
  );
};

export default AssignMembersModal;
