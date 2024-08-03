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
  UseEntityModalCustomProps,
} from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApiCall } from "src/utility/api/hooks";
import { useParams } from "react-router";
import { Role } from "src/utility/api/roles";
import { useNotifications } from "src/components/notifications";
import { ApiDefinition } from "src/utility/api/request";
import { UnassignRoleParams } from "src/utility/api/roles/assign";

const UnassignRoleModal: FC<
  UseEntityModalCustomProps<
    Role,
    {
      unassignRole: ApiDefinition<undefined, UnassignRoleParams>;
    }
  >
> = ({ entity: role, unassignRole, open, onClose, onSuccess }) => {
  const { t } = useTranslate();
  const { enqueueNotification } = useNotifications();

  const { id = "" } = useParams<{ id: string }>();
  const [callRemoveRole, { loading }] = useApiCall(unassignRole);

  const handleSubmit = async () => {
    if (role) {
      const { success } = await callRemoveRole({
        id,
        roleId: role.id,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("Role has been removed."),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t('Are you sure you want to unassign the role "{{ name }}"?', {
        name: role?.name,
      })}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Removing role")}
      onClose={onClose}
    />
  );
};

export default UnassignRoleModal;
