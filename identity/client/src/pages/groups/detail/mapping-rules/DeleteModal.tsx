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
import { MappingRule } from "src/utility/api/mapping-rules";
import { unassignGroupMappingRule } from "src/utility/api/groups";

type RemoveGroupMappingRuleModalProps = UseEntityModalCustomProps<
  MappingRule,
  {
    groupId: string;
  }
>;

const DeleteModal: FC<RemoveGroupMappingRuleModalProps> = ({
  entity: mappingRule,
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMappingRule, { loading }] = useApiCall(
    unassignGroupMappingRule,
  );

  const handleSubmit = async () => {
    if (groupId && mappingRule) {
      const { success } = await callUnassignMappingRule({
        groupId,
        mappingRuleId: mappingRule.mappingRuleId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("groupMappingRuleRemoved"),
        });
        onSuccess();
      }
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
          i18nKey="removeMappingRuleConfirmation"
          values={{ mappingRuleId: mappingRule.mappingRuleId }}
        >
          Are you sure you want to remove{" "}
          <strong>{mappingRule.mappingRuleId}</strong> from this group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
