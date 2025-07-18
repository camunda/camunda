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
import { Group } from "src/utility/api/groups";
import { unassignRoleGroup } from "src/utility/api/roles";

type RemoveRoleGroupModalProps = UseEntityModalCustomProps<
  Group,
  {
    role: string;
  }
>;

const DeleteModal: FC<RemoveRoleGroupModalProps> = ({
  entity: group,
  open,
  onClose,
  onSuccess,
  role,
}) => {
  const { t, Translate } = useTranslate("roles");
  const { enqueueNotification } = useNotifications();

  const [callUnassignGroup, { loading }] = useApiCall(unassignRoleGroup);

  const handleSubmit = async () => {
    if (role && group) {
      const { success } = await callUnassignGroup({
        roleId: role,
        groupId: group.groupId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("roleGroupRemoved"),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeGroup")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingGroup")}
      onClose={onClose}
      confirmLabel={t("removeGroup")}
    >
      <p>
        <Translate
          i18nKey="removeGroupFromRole"
          values={{ groupId: group.groupId }}
        >
          Are you sure you want to remove <strong>{group.groupId}</strong> from
          this role?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
