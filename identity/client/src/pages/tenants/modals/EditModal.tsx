/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api";
import { useNotifications } from "src/components/notifications";
import { updateTenant } from "src/utility/api/tenants";
import TextField from "src/components/form/TextField";
import type { Tenant } from "@camunda/camunda-api-zod-schemas/8.10";

const getTenantFormValues = (tenant: Tenant) => ({
  name: tenant.name ?? "",
  description: tenant.description ?? "",
});

const EditModal: FC<UseEntityModalProps<Tenant>> = ({
  entity: tenant,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const [callUpdateTenant, { error, loading }] = useApiCall(updateTenant, {
    suppressErrorNotification: true,
  });

  const initialTenantFormValues = getTenantFormValues(tenant);
  const [tenantName, setTenantName] = useState(initialTenantFormValues.name);
  const [description, setDescription] = useState(
    initialTenantFormValues.description,
  );

  useEffect(() => {
    const newTenantFormValues = getTenantFormValues(tenant);
    setTenantName(newTenantFormValues.name);
    setDescription(newTenantFormValues.description);
  }, [tenant.tenantId]);

  const handleSubmit = async () => {
    const { success } = await callUpdateTenant({
      tenantId: tenant.tenantId,
      name: tenantName,
      description,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("tenantHasBeenUpdated"),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      size="sm"
      open={open}
      headline={t("editTenant")}
      onSubmit={handleSubmit}
      onClose={onClose}
      loading={loading}
      error={error}
      loadingDescription={t("updatingTenant")}
      confirmLabel={t("editTenant")}
      submitDisabled={!tenantName}
    >
      <TextField
        label={t("tenantId")}
        value={tenant.tenantId}
        helperText={t("tenantIdHelperText")}
        readOnly
      />
      <TextField
        label={t("tenantName")}
        value={tenantName}
        placeholder={t("tenantNamePlaceholder")}
        onChange={setTenantName}
        autoFocus
      />
      <TextField
        label={t("description")}
        value={description}
        placeholder={t("tenantDescriptionPlaceholder")}
        onChange={setDescription}
        cols={2}
        enableCounter
      />
    </FormModal>
  );
};

export default EditModal;
