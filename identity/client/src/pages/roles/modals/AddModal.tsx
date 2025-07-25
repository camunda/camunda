/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField";
import { createRole } from "src/utility/api/roles";
import { isValidRoleId } from "src/pages/roles/modals/isValidRoleId";
import { useNotifications } from "src/components/notifications";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();
  const [callAddRole, { loading, error }] = useApiCall(createRole, {
    suppressErrorNotification: true,
  });

  const [roleName, setRoleName] = useState("");
  const [roleId, setRoleId] = useState("");
  const [description, setDescription] = useState("");
  const [isRoleIdValid, setIsRoleIdValid] = useState(true);

  const isSubmitDisabled = loading || !roleName || !roleId || !isRoleIdValid;

  const handleSubmit = async () => {
    const { success } = await callAddRole({
      name: roleName,
      description,
      roleId,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("roleCreated"),
        subtitle: t("createRoleSuccess", {
          roleName,
        }),
      });
      onSuccess();
    }
  };

  const validateRoleId = () => {
    setIsRoleIdValid(isValidRoleId(roleId));
  };

  return (
    <FormModal
      open={open}
      headline={t("createRole")}
      loading={loading}
      error={error}
      loadingDescription={t("creatingRole")}
      confirmLabel={t("createRole")}
      submitDisabled={isSubmitDisabled}
      onClose={onClose}
      onSubmit={handleSubmit}
    >
      <TextField
        label={t("roleId")}
        value={roleId}
        placeholder={t("roleIdPlaceholder")}
        errors={!isRoleIdValid ? [t("pleaseEnterValidRoleId")] : []}
        helperText={t("roleIdHelperText")}
        autoFocus
        onChange={(value) => {
          setRoleId(value);
        }}
        onBlur={validateRoleId}
      />
      <TextField
        label={t("roleName")}
        value={roleName}
        placeholder={t("roleNamePlaceholder")}
        onChange={setRoleName}
      />
      <TextField
        label={t("description")}
        value={description || ""}
        placeholder={t("roleDescriptionPlaceholder")}
        cols={2}
        enableCounter
        onChange={setDescription}
      />
    </FormModal>
  );
};

export default AddModal;
