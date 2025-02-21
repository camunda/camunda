/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership. Licensed under the Camunda License 1.0.
 * You may not use this file except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createTenant } from "src/utility/api/tenants";
import { useNotifications } from "src/components/notifications";
import { Stack } from "@carbon/react";
import { spacing06 } from "@carbon/elements";

const AddTenantModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(createTenant, {
    suppressErrorNotification: true,
  });
  const [name, setName] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [description, setDescription] = useState("");

  const submitDisabled = loading || !name || !tenantId;

  const handleSubmit = async () => {
    const { success } = await apiCall({
      name,
      tenantId,
      description,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Tenant created"),
        subtitle: t("You have successfully created tenant {{ name }}", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      headline={t("Create new tenant")}
      open={open}
      onClose={onClose}
      loading={loading}
      submitDisabled={submitDisabled}
      confirmLabel={t("Create tenant")}
      onSubmit={handleSubmit}
    >
      <Stack orientation="vertical" gap={spacing06}>
        <TextField
          label={t("Tenant ID")}
          placeholder={t("Enter tenant ID")}
          onChange={setTenantId}
          value={tenantId}
          helperText="The tenant ID must be unique and cannot be modified."
          autoFocus
        />
        <TextField
          label={t("Tenant name")}
          placeholder={t("Enter tenant name")}
          onChange={setName}
          value={name}
        />
        <TextField
          label={t("Description")}
          value={description}
          placeholder={t("Enter a tenant description (Max 255 characters)")}
          onChange={setDescription}
          cols={2}
          enableCounter
        />
      </Stack>
    </FormModal>
  );
};

export default AddTenantModal;
