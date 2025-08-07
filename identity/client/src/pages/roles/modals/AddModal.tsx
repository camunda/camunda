/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField";
import { createRole } from "src/utility/api/roles";
import { isValidRoleId } from "src/pages/roles/modals/isValidRoleId";
import { useNotifications } from "src/components/notifications";

type FormData = {
  roleName: string;
  roleId: string;
  description: string;
};

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();
  const [callAddRole, { loading, error }] = useApiCall(createRole, {
    suppressErrorNotification: true,
  });

  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      roleName: "",
      roleId: "",
      description: "",
    },
    mode: "all",
  });

  const onSubmit = async (data: FormData) => {
    const { success } = await callAddRole({
      name: data.roleName,
      description: data.description,
      roleId: data.roleId,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("roleCreated"),
        subtitle: t("createRoleSuccess", {
          roleName: data.roleName,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("createRole")}
      loading={loading}
      error={error}
      loadingDescription={t("creatingRole")}
      confirmLabel={t("createRole")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="roleId"
        control={control}
        rules={{
          validate: (value) =>
            isValidRoleId(value) || t("pleaseEnterValidRoleId"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("roleId")}
            placeholder={t("roleIdPlaceholder")}
            errors={fieldState.error?.message}
            helperText={t("roleIdHelperText")}
            autoFocus
          />
        )}
      />
      <Controller
        name="roleName"
        control={control}
        rules={{
          required: t("roleNameRequired"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("roleName")}
            placeholder={t("roleNamePlaceholder")}
            errors={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="description"
        control={control}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("description")}
            placeholder={t("roleDescriptionPlaceholder")}
            cols={2}
            enableCounter
          />
        )}
      />
    </FormModal>
  );
};

export default AddModal;
