/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { InlineNotification } from "@carbon/react";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { useNotifications } from "src/components/notifications";
import { Group, updateGroup } from "src/utility/api/groups";
import TextField from "src/components/form/TextField";

const EditModal: FC<UseEntityModalProps<Group>> = ({
  entity: group,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const [callUpdateGroup, { error, loading }] = useApiCall(updateGroup, {
    suppressErrorNotification: true,
  });

  const [name, setName] = useState(group.name);

  const handleSubmit = async () => {
    const { success } = await callUpdateGroup({
      groupKey: group.groupKey,
      name: name.trim(),
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Group has been updated."),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      size="sm"
      open={open}
      headline={t("Rename group")}
      onSubmit={handleSubmit}
      onClose={onClose}
      loading={loading}
      loadingDescription={t("Updating group")}
      confirmLabel={t("Edit")}
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

export default EditModal;
