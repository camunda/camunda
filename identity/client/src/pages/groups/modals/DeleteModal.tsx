/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { useNotifications } from "src/components/notifications";
import { deleteGroup, Group } from "src/utility/api/groups";

const DeleteModal: FC<UseEntityModalProps<Group>> = ({
  entity: group,
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const [callDeleteGroup, { loading }] = useApiCall(deleteGroup);

  const handleSubmit = async () => {
    const { success } = await callDeleteGroup({
      groupKey: group.groupKey,
    });
    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Group has been deleted."),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t('Are you sure you want to delete the group "{{ name }}"?', {
        name: group?.name,
      })}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Deleting group")}
      onClose={onClose}
    />
  );
};

export default DeleteModal;
