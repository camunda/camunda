/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { deleteUser, User } from "src/utility/api/users";
import { useNotifications } from "src/components/notifications";

const DeleteModal: FC<UseEntityModalProps<User>> = ({
  open,
  onClose,
  onSuccess,
  entity: { username },
}) => {
  const { t, Translate } = useTranslate();
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(deleteUser);

  const handleSubmit = async () => {
    const { success } = await apiCall({
      username: username!,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("userDeleted"),
      });
      onSuccess();
    }
  };

  return (
    <Modal
      open={open}
      headline={t("deleteUser")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingUser")}
      onClose={onClose}
      confirmLabel={t("Delete user")}
    >
      <p>
        <Translate i18nKey="confirmDeleteUser" values={{ username }}>
          Are you sure you want to delete the user <strong>{username}</strong>?
        </Translate>{" "}
        {t("actionCannotBeUndone")}
      </p>
    </Modal>
  );
};

export default DeleteModal;
