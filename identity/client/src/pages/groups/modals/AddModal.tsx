/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { FormModal, UseModalProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import TextField from "src/components/form/TextField";
import { createGroup } from "src/utility/api/groups";
import { isValidGroupId } from "./isValidGroupId";

const AddModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("groups");

  const [callAddGroup, { loading, error }] = useApiCall(createGroup, {
    suppressErrorNotification: true,
  });

  const [groupName, setGroupName] = useState("");
  const [groupId, setGroupId] = useState("");
  const [description, setDescription] = useState("");
  const [isGroupIdValid, setIsGroupIdValid] = useState(true);

  const handleSubmit = async () => {
    const { success } = await callAddGroup({
      name: groupName.trim(),
    });

    if (success) {
      onSuccess();
    }
  };

  const validateGroupId = (id: string) => {
    setIsGroupIdValid(isValidGroupId(id));
  };

  return (
    <FormModal
      open={open}
      headline={t("createGroup")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      error={error}
      loadingDescription={t("creatingGroup")}
      confirmLabel={t("createGroup")}
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
        helperText={t("groupIdHelperText")}
        autoFocus
      />
      <TextField
        label={t("groupName")}
        value={groupName}
        placeholder={t("groupNamePlaceholder")}
        onChange={setGroupName}
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

export default AddModal;
