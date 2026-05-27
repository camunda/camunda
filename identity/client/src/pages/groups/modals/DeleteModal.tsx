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
import { useNotifications } from "src/components/notifications";
import { useDeleteGroup } from "src/utility/api/groups/hooks";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

const DeleteModal: FC<UseEntityModalProps<Group>> = ({
  entity: { groupId },
  open,
  onClose,
  onSuccess,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const { mutate, isPending: loading } = useDeleteGroup();

  const handleSubmit = () => {
    mutate(
      { groupId },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("groupHasBeenDeleted"),
          });
          onSuccess();
        },
      },
    );
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
        <Translate i18nKey="deleteGroupConfirmation" values={{ groupId }}>
          Are you sure you want to delete <strong>{groupId}</strong>? This
          action cannot be undone.
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
