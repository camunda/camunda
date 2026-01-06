/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { assignGroupMember } from "src/utility/api/membership";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import { User } from "src/utility/api/users";
import FormModal from "src/components/modal/FormModal";
import { Group } from "src/utility/api/groups";
import TextField from "src/components/form/TextField";
import { useNotifications } from "src/components/notifications";

const AssignMemberModal: FC<
  UseEntityModalCustomProps<
    { groupId: Group["groupId"] },
    { assignedUsers: User[] }
  >
> = ({ entity: { groupId }, onSuccess, open, onClose }) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [username, setUsername] = useState("");
  const [loadingAssignUser, setLoadingAssignUser] = useState(false);

  const [callAssignUser] = useApiCall(assignGroupMember);

  const canSubmit = groupId && username;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignUser(true);
    const { success } = await callAssignUser({ username, groupId });
    setLoadingAssignUser(false);

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("userAssigned"),
        subtitle: t("userAssignedSuccessfully"),
      });
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setUsername("");
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
      <p>{t("assignUsersToGroup")}</p>
      <TextField
        label={t("username")}
        placeholder={t("typeUsername")}
        onChange={setUsername}
        value={username}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignMemberModal;
