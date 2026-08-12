/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { membershipMutations } from "src/utility/api/membership/mutations";
import { UserMultiSelect } from "src/components/form/entitySelection/UserSelection";
import FormModal from "src/components/modal/FormModal";
import { useNotifications } from "src/components/notifications";
import type { Group, User } from "@camunda/camunda-api-zod-schemas/8.10";

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
      <UserMultiSelect
        value={selectedUsers}
        onChange={setSelectedUsers}
        excluded={assignedUsers}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignMembersModal;
