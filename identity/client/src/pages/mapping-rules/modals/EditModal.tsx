/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { Stack } from "@carbon/react";
import { spacing05 } from "@carbon/elements";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { updateMappingRule, MappingRule } from "src/utility/api/mapping-rules";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "./components";
import { useNotifications } from "src/components/notifications";

const EditModal: FC<UseEntityModalProps<MappingRule>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [callUpdateMappingRule, { loading, error }] = useApiCall(
    updateMappingRule,
    {
      suppressErrorNotification: true,
    },
  );
  const [mappingRule, setMappingRule] = useState<MappingRule>(entity);

  const handleSubmit = async () => {
    const { success } = await callUpdateMappingRule(mappingRule);
    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("mappingRuleUpdated"),
        subtitle: t("mappingRuleUpdatedSuccessfully", {
          name: mappingRule.name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      open={open}
      headline={t("editMappingRule")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      error={error}
      loadingDescription={t("updatingMappingRule")}
      confirmLabel={t("updateMappingRule")}
    >
      <TextField
        label={t("mappingRuleId")}
        onChange={(mappingRuleId) =>
          setMappingRule((mappingRule) => ({
            ...mappingRule,
            mappingRuleId: mappingRuleId,
          }))
        }
        value={mappingRule.mappingRuleId}
        readOnly
      />
      <TextField
        label={t("mappingRuleName")}
        placeholder={t("enterMappingRuleName")}
        onChange={(name) =>
          setMappingRule((mappingRule) => ({ ...mappingRule, name }))
        }
        value={mappingRule.name}
        helperText={t("uniqueNameForMappingRule")}
        autoFocus
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>{t("mappingRule")}</h3>
          <CustomStack orientation="horizontal">
            <TextField
              label={t("claimName")}
              placeholder={t("enterClaimName")}
              onChange={(claimName) =>
                setMappingRule((mappingRule) => ({ ...mappingRule, claimName }))
              }
              value={mappingRule.claimName}
              helperText={t("customClaimName")}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <TextField
              label={t("claimValue")}
              placeholder={t("enterClaimValue")}
              onChange={(claimValue) =>
                setMappingRule((mappingRule) => ({
                  ...mappingRule,
                  claimValue,
                }))
              }
              value={mappingRule.claimValue}
              helperText={t("valueForClaim")}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};

export default EditModal;
