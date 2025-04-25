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
import { unassignGroupMember } from "src/utility/api/membership";

type RemoveGroupMemberModalProps = UseEntityModalCustomProps<
  User,
  {
    groupId: string;
  }
>;

const DeleteModal: FC<RemoveGroupMemberModalProps> = ({
  entity: user,
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMember, { loading }] = useApiCall(unassignGroupMember);

  const handleSubmit = async () => {
    if (groupId && user) {
      const { success } = await callUnassignMember({
        groupId,
        username: user.username,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("groupMemberRemoved"),
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
          this group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
