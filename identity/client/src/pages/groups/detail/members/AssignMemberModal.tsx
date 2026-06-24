/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UseEntityModalCustomProps } from "src/components/modal";
import { membershipMutations } from "src/utility/api/membership/mutations";
import useTranslate from "src/utility/localization";
import FormModal from "src/components/modal/FormModal";
import TextField from "src/components/form/TextField";
import { useNotifications } from "src/components/notifications";
import { DocumentationLink } from "src/components/documentation";
import { Caption } from "src/pages/authorizations/modals/components.tsx";
import type { Group, User } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignMemberModal: FC<
  UseEntityModalCustomProps<
    { groupId: Group["groupId"] },
    { assignedUsers: User[] }
  >
> = ({ entity: { groupId }, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [username, setUsername] = useState("");
  const qc = useQueryClient();
  const { mutate, isPending: loadingAssignUser } = useMutation(
    membershipMutations.assignGroupMember(qc),
  );

  const canSubmit = groupId && username;

  const handleSubmit = () => {
    if (!canSubmit) return;
    mutate(
      { username, groupId },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("userAssigned"),
            subtitle: t("userAssignedSuccessfully"),
          });
          onSuccess();
        },
      },
    );
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
        helperText={
          <Caption>
            <Translate i18nKey="usernameDescription">
              Check the documentation for{" "}
              <DocumentationLink
                path="/components/admin/group/#assign-users-to-a-group"
                withIcon
              >
                how to reference users
              </DocumentationLink>{" "}
              .
            </Translate>
          </Caption>
        }
        value={username}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignMemberModal;
