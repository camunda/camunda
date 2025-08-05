/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { Stack } from "@carbon/react";
import { spacing06 } from "@carbon/elements";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createTenant } from "src/utility/api/tenants";
import { useNotifications } from "src/components/notifications";
import { isValidTenantId } from "src/pages/tenants/modals/isValidTenantId";

type FormData = {
  name: string;
  tenantId: string;
  description: string;
};

const AddTenantModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();
  const [callAddTenant, { loading, error }] = useApiCall(createTenant, {
    suppressErrorNotification: true,
  });

  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      name: "",
      tenantId: "",
      description: "",
    },
    mode: "all",
  });

  const onSubmit = async (data: FormData) => {
    const { success } = await callAddTenant({
      name: data.name,
      tenantId: data.tenantId,
      description: data.description,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("tenantCreated"),
        subtitle: t("createTenantSuccess", {
          name: data.name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("createNewTenant")}
      loading={loading}
      error={error}
      loadingDescription={t("creatingTenant")}
      confirmLabel={t("createTenant")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Stack orientation="vertical" gap={spacing06}>
        <Controller
          name="tenantId"
          control={control}
          rules={{
            validate: (value) =>
              isValidTenantId(value) || t("pleaseEnterValidTenantId"),
          }}
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              label={t("tenantId")}
              placeholder={t("tenantIdPlaceholder")}
              errors={fieldState.error?.message}
              helperText={t("tenantIdHelperText")}
              autoFocus
            />
          )}
        />
        <Controller
          name="name"
          control={control}
          rules={{
            required: t("tenantNameRequired"),
          }}
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              label={t("tenantName")}
              placeholder={t("tenantNamePlaceholder")}
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
              placeholder={t("tenantDescriptionPlaceholder")}
              cols={2}
              enableCounter
            />
          )}
        />
      </Stack>
    </FormModal>
  );
};

export default AddTenantModal;
