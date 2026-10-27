/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import TextField from "src/components/form/TextField";
import { roleMutations } from "src/utility/api/roles/mutations";
import { useNotifications } from "src/components/notifications";
import { isValidId, getIdPattern } from "src/utility/validate";

type FormData = {
  roleName: string;
  roleId: string;
  description: string;
};

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(roleMutations.create(qc));

  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      roleName: "",
      roleId: "",
      description: "",
    },
    mode: "all",
  });

  const onSubmit = (data: FormData) => {
    mutate(
      {
        name: data.roleName,
        description: data.description,
        roleId: data.roleId,
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("roleCreated"),
            subtitle: t("createRoleSuccess", {
              roleName: data.roleName,
            }),
          });
          onSuccess();
        },
      },
    );
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
            isValidId(value) ||
            t("pleaseEnterValidRoleId", {
              pattern: getIdPattern(),
            }),
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
