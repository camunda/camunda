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
import { useUnassignTenantMappingRule } from "src/utility/api/tenants/hooks";
import type { MappingRule } from "@camunda/camunda-api-zod-schemas/8.10";

type RemoveTenantMappingRuleModalProps = UseEntityModalCustomProps<
  MappingRule,
  {
    tenant: string;
  }
>;

const DeleteModal: FC<RemoveTenantMappingRuleModalProps> = ({
  entity: mappingRule,
  open,
  onClose,
  onSuccess,
  tenant,
}) => {
  const { t, Translate } = useTranslate("tenants");
  const { enqueueNotification } = useNotifications();

  const { mutate, isPending: loading } = useUnassignTenantMappingRule();

  const handleSubmit = () => {
    if (tenant && mappingRule) {
      mutate(
        { tenantId: tenant, mappingRuleId: mappingRule.mappingRuleId },
        {
          onSuccess: () => {
            enqueueNotification({
              kind: "success",
              title: t("tenantMappingRuleRemoved"),
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
      headline={t("removeMappingRule")}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("removingMappingRule")}
      onClose={onClose}
      confirmLabel={t("removeMappingRule")}
    >
      <p>
        <Translate
          i18nKey="removeMappingRuleFromTenant"
          values={{ mappingRuleId: mappingRule.mappingRuleId }}
        >
          Are you sure you want to remove{" "}
          <strong>{mappingRule.mappingRuleId}</strong> from this tenant?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
