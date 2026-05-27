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
import { useAssignTenantMember } from "src/utility/api/membership/hooks";
import useTranslate from "src/utility/localization";
import { useSearchUsers } from "src/utility/api/users/hooks";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import type { Tenant, User } from "@camunda/camunda-api-zod-schemas/8.10";

const SelectedUsers = styled.div`
  margin-top: 0;
`;

const AssignMembersModal: FC<
  UseEntityModalCustomProps<
    { tenantId: Tenant["tenantId"] },
    { assignedUsers: User[] }
  >
> = ({ entity: { tenantId }, assignedUsers, onSuccess, open, onClose }) => {
  const { t } = useTranslate("tenants");
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [loadingAssignUser, setLoadingAssignUser] = useState(false);

  const [search, setSearch] = useState<Record<string, unknown>>({});
  const handleSearchChange = (search: string) => {
    if (search === "") {
      setSearch({});
      return;
    }
    setSearch({ filter: { username: { $like: `*${search}*` } } });
  };

  const {
    data: userSearchResults,
    isLoading: loading,
    refetch: reload,
    error,
  } = useSearchUsers(search);

  const { mutateAsync: callAssignUser } = useAssignTenantMember();

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

  const canSubmit = tenantId && selectedUsers.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignUser(true);
    try {
      await Promise.all(
        selectedUsers.map(({ username }) =>
          callAssignUser({ username, tenantId }),
        ),
      );
      onSuccess();
    } catch {
      // error handled globally
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
      <p>{t("searchAndAssignUsersToTenant")}</p>
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
      <DropdownSearch
        autoFocus
        items={userSearchResults?.items || []}
        itemTitle={({ username }) => username}
        itemSubTitle={({ email }) => email}
        placeholder={t("searchByNameOrEmail")}
        onSelect={onSelectUser}
        onChange={handleSearchChange}
        filter={unassignedFilter}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("usersCouldNotLoad")}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </FormModal>
  );
};

export default AssignMembersModal;
