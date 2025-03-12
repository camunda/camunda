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
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callDeleteGroup, { loading }] = useApiCall(deleteGroup);

  const handleSubmit = async () => {
    const { success } = await callDeleteGroup({
      groupKey: group.groupKey,
    });
    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("groupHasBeenDeleted"),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteGroup")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingGroup")}
      onClose={onClose}
      confirmLabel={t("deleteGroup")}
    >
      <p>
        <Translate
          i18nKey="deleteGroupConfirmation"
          values={{ groupKey: group.groupKey }}
        >
          Are you sure you want to delete <strong>{group.groupKey}</strong>?{" "}
          This action cannot be undone.
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
