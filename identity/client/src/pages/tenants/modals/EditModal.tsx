/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { UpdateTenantParams, updateTenant } from "src/utility/api/tenants";
import { useNotifications } from "src/components/notifications";

const EditTenantModal: FC<UseEntityModalProps<UpdateTenantParams>> = ({
  open,
  onClose,
  onSuccess,
  entity: { tenantId, name: currentName, description: currentDescription },
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, namedErrors }] = useApiCall(updateTenant);
  const [name, setName] = useState(currentName);
  const [description, setDescription] = useState(currentDescription);

  const handleSubmit = async () => {
    const { success } = await apiCall({
      tenantId: tenantId,
      name: name,
      description: description ?? "",
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Tenant update"),
        subtitle: t("You have successfully updated tenant {{ name }}", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("Edit tenant")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Updating tenant")}
      confirmLabel={t("Update tenant")}
    >
      <TextField
        label={t("Name")}
        value={name}
        placeholder={t("Name")}
        onChange={setName}
        errors={namedErrors?.name}
        autoFocus
      />
      <TextField
        label={t("Description")}
        value={description}
        placeholder={t("Tenant description")}
        onChange={setDescription}
        errors={namedErrors?.description}
      />
    </FormModal>
  );
};

export default EditTenantModal;
