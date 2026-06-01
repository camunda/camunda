/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalProps,
} from "src/components/modal";
import { roleMutations } from "src/utility/api/roles/mutations";
import { useNotifications } from "src/components/notifications";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

const DeleteModal: FC<UseEntityModalProps<Role>> = ({
  open,
  onClose,
  onSuccess,
  entity: { roleId, name },
}) => {
  const { t, Translate } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const { mutate, isPending: loading } = useMutation(roleMutations.delete(qc));

  const handleSubmit = () => {
    mutate(
      { roleId },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("roleHasBeenDeleted"),
            subtitle: t("deleteRoleSuccess", { name }),
          });
          onSuccess();
        },
      },
    );
  };

  return (
    <Modal
      open={open}
      headline={t("deleteRole")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("deletingRole")}
      onClose={onClose}
      confirmLabel={t("deleteRole")}
    >
      <p>
        <Translate i18nKey="deleteRoleConfirmation" values={{ roleId }}>
          Are you sure you want to delete <strong>{roleId}</strong>?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
