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
import { roleMutations } from "src/utility/api/roles/mutations";
import useTranslate from "src/utility/localization";
import FormModal from "src/components/modal/FormModal";
import TextField from "src/components/form/TextField";
import type { Group, Role } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignGroupModal: FC<
  UseEntityModalCustomProps<
    { roleId: Role["roleId"] },
    { assignedGroups: Group[] }
  >
> = ({ entity: { roleId }, onSuccess, open, onClose }) => {
  const { t } = useTranslate("roles");
  const [groupId, setGroupId] = useState("");
  const qc = useQueryClient();
  const { mutate, isPending: loadingAssignGroup } = useMutation(
    roleMutations.assignGroup(qc),
  );

  const canSubmit = roleId && groupId;

  const handleSubmit = () => {
    if (!canSubmit) return;
    mutate({ groupId, roleId }, { onSuccess });
  };

  useEffect(() => {
    if (open) {
      setGroupId("");
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignGroup")}
      confirmLabel={t("assignGroup")}
      loading={loadingAssignGroup}
      loadingDescription={t("assigningGroup")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>{t("assignGroupsToRole")}</p>
      <TextField
        label={t("groupId")}
        placeholder={t("typeGroup")}
        onChange={setGroupId}
        value={groupId}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignGroupModal;
