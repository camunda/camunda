/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useNotifications } from "src/components/notifications";
import { roleMutations } from "src/utility/api/roles/mutations";
import TextField from "src/components/form/TextField";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

const EditModal: FC<UseEntityModalProps<Role>> = ({
  entity: role,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();

  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(roleMutations.update(qc));

  const [roleName, setRoleName] = useState(role.name ?? "");
  const [description, setDescription] = useState(role.description ?? "");

  const handleSubmit = () => {
    mutate(
      { roleId: role.roleId, name: roleName, description },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("roleHasBeenUpdated"),
          });
          onSuccess();
        },
      },
    );
  };

  return (
    <FormModal
      size="sm"
      open={open}
      headline={t("editRole")}
      onSubmit={handleSubmit}
      onClose={onClose}
      loading={loading}
      error={error}
      loadingDescription={t("updatingRole")}
      confirmLabel={t("editRole")}
      submitDisabled={!roleName}
    >
      <TextField label={t("roleId")} value={role.roleId} readOnly />
      <TextField
        label={t("roleName")}
        value={roleName}
        placeholder={t("roleNamePlaceholder")}
        onChange={setRoleName}
        autoFocus
      />
      <TextField
        label={t("description")}
        value={description}
        placeholder={t("roleDescriptionPlaceholder")}
        onChange={setDescription}
        cols={2}
        enableCounter
      />
    </FormModal>
  );
};

export default EditModal;
