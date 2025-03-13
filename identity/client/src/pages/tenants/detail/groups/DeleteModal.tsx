/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
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
import { unassignTenantGroup } from "src/utility/api/tenants";

type RemoveTenantGroupModalProps = UseEntityModalCustomProps<
  Group,
  {
    tenant: string;
  }
>;

const DeleteModal: FC<RemoveTenantGroupModalProps> = ({
  entity: group,
  open,
  onClose,
  onSuccess,
  tenant,
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const [callUnassignGroup, { loading }] = useApiCall(unassignTenantGroup);

  const handleSubmit = async () => {
    if (tenant && group) {
      const { success } = await callUnassignGroup({
        tenantId: tenant,
        groupKey: group.groupKey,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("tenantGroupRemoved"),
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
          i18nKey="removeGroupFromTenant"
          values={{ groupKey: group.groupKey }}
        >
          Are you sure you want to remove <strong>{group.groupKey}</strong> from
          this tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
