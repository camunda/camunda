/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { InlineNotification } from "@carbon/react";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import TextField from "src/components/form/TextField";
import { createRole } from "src/utility/api/roles";
import { isValidRoleId } from "src/pages/roles/modals/isValidRoleId";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("roles");

  const [callAddRole, { loading, error }] = useApiCall(createRole, {
    suppressErrorNotification: true,
  });

  const [roleName, setRoleName] = useState("");
  const [roleId, setRoleId] = useState("");
  const [description, setDescription] = useState("");
  const [isRoleIdValid, setIsRoleIdValid] = useState(true);

  const handleSubmit = async () => {
    const { success } = await callAddRole({
      name: roleName.trim(),
    });

    if (success) {
      onSuccess();
    }
  };

  const validateRoleId = (id: string) => {
    setIsRoleIdValid(isValidRoleId(id));
  };

  return (
    <FormModal
      open={open}
      headline={t("createRole")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("creatingRole")}
      confirmLabel={t("createRole")}
      submitDisabled={!roleName || !roleId || !isRoleIdValid}
    >
      <TextField
        label={t("roleId")}
        value={roleId}
        placeholder={t("roleIdPlaceholder")}
        onChange={(value) => {
          validateRoleId(value);
          setRoleId(value);
        }}
        errors={!isRoleIdValid ? [t("pleaseEnterValidRoleId")] : []}
        helperText={t("roleIdHelperText")}
        autoFocus
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
        onChange={setDescription}
        cols={2}
        enableCounter
      />
      {error && (
        <InlineNotification
          kind="error"
          role="alert"
          lowContrast
          title={error.title}
          subtitle={error.detail}
        />
      )}
    </FormModal>
  );
};

export default AddModal;
