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
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { membershipMutations } from "src/utility/api/membership/mutations";
import type { User } from "@camunda/camunda-api-zod-schemas/8.10";

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

  const qc = useQueryClient();
  const { mutate, isPending: loading } = useMutation(
    membershipMutations.unassignRoleMember(qc),
  );

  const handleSubmit = () => {
    if (roleId && user) {
      mutate(
        { roleId, username: user.username },
        {
          onSuccess: () => {
            enqueueNotification({
              kind: "success",
              title: t("roleMemberRemoved"),
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
