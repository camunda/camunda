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
import { MappingRule } from "src/utility/api/mappings";
import { unassignGroupMappingRule } from "src/utility/api/groups";

type RemoveGroupMappingModalProps = UseEntityModalCustomProps<
  MappingRule,
  {
    groupId: string;
  }
>;

const DeleteModal: FC<RemoveGroupMappingModalProps> = ({
  entity: mapping,
  open,
  onClose,
  onSuccess,
  groupId,
}) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();

  const [callUnassignMapping, { loading }] = useApiCall(
    unassignGroupMappingRule,
  );

  const handleSubmit = async () => {
    if (groupId && mapping) {
      const { success } = await callUnassignMapping({
        groupId,
        mappingRuleId: mapping.mappingRuleId,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("groupMappingRemoved"),
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
          i18nKey="removeMappingConfirmation"
          values={{ mappingRuleId: mapping.mappingRuleId }}
        >
          Are you sure you want to remove{" "}
          <strong>{mapping.mappingRuleId}</strong> from this group?
        </Translate>
      </p>
    </Modal>
  );
};

export default DeleteModal;
