/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { useUnassignGroupRole } from "src/utility/api/groups/hooks";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

type RemoveGroupRoleModalProps = UseEntityModalCustomProps<
  Role,
  {
    groupId: string;
  }
>;

const DeleteModal: FC<RemoveGroupRoleModalProps> = ({
  entity: { roleId },
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const { mutate, isPending: loading } = useUnassignGroupRole();

  const handleSubmit = () => {
    if (groupId && roleId) {
      mutate(
        { groupId, roleId },
        {
          onSuccess: () => {
            enqueueNotification({
              kind: "success",
              title: t("groupRoleRemoved"),
            });
            onSuccess();
          },
        },
      );
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeRole")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingRole")}
      onClose={onClose}
      confirmLabel={t("removeRole")}
    >
      <p>
        <Translate i18nKey="removeRoleConfirmation" values={{ roleId }}>
          Are you sure you want to remove <strong>{roleId}</strong> from this
          group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
