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
import { createGroup } from "src/utility/api/groups";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate();

  const [callAddGroup, { loading, error }] = useApiCall(createGroup, {
    suppressErrorNotification: true,
  });

  const [name, setName] = useState("");

  const handleSubmit = async () => {
    const { success } = await callAddGroup({
      name: name.trim(),
    });

    if (success) {
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("Create group")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Adding group")}
      confirmLabel={t("Create group")}
    >
      <TextField
        label={t("Name")}
        value={name}
        placeholder={t("My group")}
        onChange={setName}
        autoFocus
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
