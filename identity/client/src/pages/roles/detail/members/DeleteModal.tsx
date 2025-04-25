/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useApiCall } from "src/utility/api/hooks";
import useTranslate from "src/utility/localization";
import {
  DeleteModal as Modal,
  UseEntityModalCustomProps,
} from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { User } from "src/utility/api/users";
import { unassignRoleMember } from "src/utility/api/membership";

type RemoveRoleMemberModalProps = UseEntityModalCustomProps<
  User,
  {
    roleId: string;
  }
>;

const DeleteModal: FC<RemoveRoleMemberModalProps> = ({
  entity: user,
  open,
  onClose,
  onSuccess,
  roleId,
}) => {
  const { t, Translate } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMember, { loading }] = useApiCall(unassignRoleMember);

  const handleSubmit = async () => {
    if (roleId && user) {
      const { success } = await callUnassignMember({
        roleId,
        username: user.username,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("roleMemberRemoved"),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeUser")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingUser")}
      onClose={onClose}
      confirmLabel={t("removeUser")}
    >
      <p>
        <Translate
          i18nKey="removeUserConfirmation"
          values={{ username: user.username }}
        >
          Are you sure you want to remove <strong>{user.username}</strong> from
          this role?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
