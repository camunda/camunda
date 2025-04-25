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
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { Role } from "src/utility/api/roles";
import { unassignGroupRole } from "src/utility/api/groups";

type RemoveGroupRoleModalProps = UseEntityModalCustomProps<
  Role,
  {
    groupId: string;
  }
>;

const DeleteModal: FC<RemoveGroupRoleModalProps> = ({
  entity: role,
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUnassignRole, { loading }] = useApiCall(unassignGroupRole);

  const handleSubmit = async () => {
    if (groupId && role) {
      const { success } = await callUnassignRole({
        groupId,
        roleKey: role.roleKey,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("groupRoleRemoved"),
        });
        onSuccess();
      }
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
        <Translate
          i18nKey="removeRoleConfirmation"
          values={{ roleKey: role.roleKey }}
        >
          Are you sure you want to remove <strong>{role.roleKey}</strong> from
          this group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
