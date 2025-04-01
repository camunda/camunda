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
import { Mapping } from "src/utility/api/mappings";
import { unassignTenantMapping } from "src/utility/api/tenants";

type RemoveTenantMappingModalProps = UseEntityModalCustomProps<
  Mapping,
  {
    tenant: string;
  }
>;

const DeleteModal: FC<RemoveTenantMappingModalProps> = ({
  entity: mapping,
  open,
  onClose,
  onSuccess,
  tenant,
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMapping, { loading }] = useApiCall(unassignTenantMapping);

  const handleSubmit = async () => {
    if (tenant && mapping) {
      const { success } = await callUnassignMapping({
        tenantId: tenant,
        mappingId: mapping.mappingId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("tenantMappingRemoved"),
        });
        onSuccess();
      }
    }
  };

  return (
    <Modal
      open={open}
      headline={t("removeMapping")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingMapping")}
      onClose={onClose}
      confirmLabel={t("removeMapping")}
    >
      <p>
        <Translate
          i18nKey="removeMappingFromTenant"
          values={{ mappingId: mapping.id }}
        >
          Are you sure you want to remove <strong>{mapping.id}</strong> from
          this tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
