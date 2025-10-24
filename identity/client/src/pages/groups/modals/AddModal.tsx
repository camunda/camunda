/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import TextField from "src/components/form/TextField";
import { createGroup } from "src/utility/api/groups";
import { isValidGroupId } from "./isValidGroupId";
import { useNotifications } from "src/components/notifications";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [callAddGroup, { loading, error }] = useApiCall(createGroup, {
    suppressErrorNotification: true,
  });
  type FormData = {
    groupId: string;
    groupName: string;
    description: string;
  };

  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      groupId: "",
      groupName: "",
      description: "",
    },
    mode: "all",
  });

  const onSubmit = async (data: FormData) => {
    const { success } = await callAddGroup({
      name: data.groupName,
      groupId: data.groupId,
      description: data.description,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("groupCreated"),
        subtitle: t("groupCreatedSuccessfully", {
          groupName: data.groupName,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("createGroup")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      error={error}
      loadingDescription={t("creatingGroup")}
      confirmLabel={t("createGroup")}
    >
      <Controller
        name="groupId"
        control={control}
        rules={{
          required: t("groupIdRequired"),
          validate: (value) =>
            isValidGroupId(value) || t("pleaseEnterValidGroupId"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("groupId")}
            placeholder={t("groupIdPlaceholder")}
            helperText={t("groupIdHelperText")}
            errors={fieldState.error?.message}
            autoFocus
          />
        )}
      />
      <Controller
        name="groupName"
        control={control}
        rules={{ required: t("groupNameRequired") }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("groupName")}
            placeholder={t("groupNamePlaceholder")}
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
            placeholder={t("groupDescriptionPlaceholder")}
            cols={2}
            enableCounter
          />
        )}
      />
    </FormModal>
  );
};

export default AddModal;
