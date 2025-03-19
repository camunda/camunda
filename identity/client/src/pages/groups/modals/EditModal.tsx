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
import { isValidGroupId } from "./isValidGroupId";

const EditModal: FC<UseEntityModalProps<Group>> = ({
  entity: group,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUpdateGroup, { error, loading }] = useApiCall(updateGroup, {
    suppressErrorNotification: true,
  });

  const [groupName, setGroupName] = useState(group.name);
  const [groupId, setGroupId] = useState(group.groupKey);
  const [description, setDescription] = useState(group.description);
  const [isGroupIdValid, setIsGroupIdValid] = useState(true);

  const handleSubmit = async () => {
    const { success } = await callUpdateGroup({
      groupKey: group.groupKey,
      name: groupName.trim(),
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("groupHasBeenUpdated"),
      });
      onSuccess();
    }
  };

  const validateGroupId = (id: string) => {
    setIsGroupIdValid(isValidGroupId(id));
  };

  return (
    <FormModal
      size="sm"
      open={open}
      headline={t("editGroup")}
      onSubmit={handleSubmit}
      onClose={onClose}
      loading={loading}
      loadingDescription={t("updatingGroup")}
      confirmLabel={t("editGroup")}
      submitDisabled={!groupName || !groupId || !isGroupIdValid}
    >
      <TextField
        label={t("groupId")}
        value={groupId}
        placeholder={t("groupIdPlaceholder")}
        onChange={(value) => {
          validateGroupId(value);
          setGroupId(value);
        }}
        errors={!isGroupIdValid ? [t("pleaseEnterValidGroupId")] : []}
        readOnly
      />
      <TextField
        label={t("groupName")}
        value={groupName}
        placeholder={t("groupNamePlaceholder")}
        onChange={setGroupName}
        autoFocus
      />
      <TextField
        label={t("description")}
        value={description || ""}
        placeholder={t("groupDescriptionPlaceholder")}
        onChange={setDescription}
        cols={2}
        enableCounter
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
