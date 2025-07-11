/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { Stack } from "@carbon/react";
import { spacing06 } from "@carbon/elements";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createTenant } from "src/utility/api/tenants";
import { useNotifications } from "src/components/notifications";
import { ErrorResponse } from "src/utility/api/request";
import { isValidTenantId } from "./isValidTenantId";

const AddTenantModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(createTenant, {
    suppressErrorNotification: true,
  });
  const [name, setName] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [description, setDescription] = useState("");
  const [isTenantIdValid, setIsTenantIdValid] = useState(true);

  const isSubmitDisabled = loading || !name || !tenantId || !isTenantIdValid;

  const validateTenantId = () => {
    setIsTenantIdValid(isValidTenantId(tenantId));
  };

  const handleSubmit = async () => {
    const { success, error } = await apiCall({
      name,
      tenantId,
      description,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("tenantCreated"),
        subtitle: t("createTenantSuccess", {
          name,
        }),
      });
      onSuccess();
    } else {
      const detail = (error as ErrorResponse<"detailed">)?.detail;

      enqueueNotification({
        kind: "error",
        title: t("failedToCreateTenant"),
        subtitle: detail,
      });
    }
  };

  return (
    <FormModal
      headline={t("createNewTenant")}
      open={open}
      onClose={onClose}
      loading={loading}
      submitDisabled={isSubmitDisabled}
      confirmLabel={t("createTenant")}
      onSubmit={handleSubmit}
    >
      <Stack orientation="vertical" gap={spacing06}>
        <TextField
          label={t("tenantId")}
          placeholder={t("tenantIdPlaceholder")}
          onChange={(value) => {
            setTenantId(value);
          }}
          onBlur={validateTenantId}
          value={tenantId}
          errors={!isTenantIdValid ? [t("pleaseEnterValidTenantId")] : []}
          helperText={t("tenantIdHelperText")}
          autoFocus
        />
        <TextField
          label={t("tenantName")}
          placeholder={t("tenantNamePlaceholder")}
          onChange={setName}
          value={name}
        />
        <TextField
          label={t("description")}
          value={description}
          placeholder={t("tenantDescriptionPlaceholder")}
          onChange={setDescription}
          cols={2}
          enableCounter
        />
      </Stack>
    </FormModal>
  );
};

export default AddTenantModal;
