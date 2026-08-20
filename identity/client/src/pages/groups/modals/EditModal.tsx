/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useNotifications } from "src/components/notifications";
import { groupMutations } from "src/utility/api/groups/mutations";
import TextField from "src/components/form/TextField";
import { isValidId } from "src/utility/validate.ts";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

const EditModal: FC<UseEntityModalProps<Group>> = ({
  entity: group,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(groupMutations.update(qc));

  const [groupName, setGroupName] = useState(group.name);
  const [groupId, setGroupId] = useState(group.groupId);
  const [description, setDescription] = useState(group.description);
  const [isGroupIdValid, setIsGroupIdValid] = useState(true);

  const handleSubmit = () => {
    mutate(
      {
        groupId: group.groupId,
        name: groupName,
        description: description,
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("groupHasBeenUpdated"),
          });
          onSuccess();
        },
      },
    );
  };

  const validateGroupId = (id: string) => {
    setIsGroupIdValid(isValidId(id));
  };

  return (
    <FormModal
      size="sm"
      open={open}
      headline={t("editGroup")}
      onSubmit={handleSubmit}
      onClose={onClose}
      loading={loading}
      error={error}
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
    </FormModal>
  );
};

export default EditModal;
