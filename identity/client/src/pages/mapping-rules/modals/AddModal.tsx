/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { createMappingRule } from "src/utility/api/mapping-rules";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "./components";
import { spacing05 } from "@carbon/elements";
import { Stack } from "@carbon/react";

const AddMappingRuleModal: FC<UseModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, error }] = useApiCall(createMappingRule, {
    suppressErrorNotification: true,
  });
  const [mappingRuleId, setMappingRuleId] = useState("");
  const [mappingRuleName, setMappingRuleName] = useState("");
  const [claimName, setClaimName] = useState("");
  const [claimValue, setClaimValue] = useState("");

  const submitDisabled = loading || !mappingRuleName;

  const handleSubmit = async () => {
    const { success } = await apiCall({
      mappingRuleId: mappingRuleId,
      name: mappingRuleName,
      claimName,
      claimValue,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("mappingRuleCreated"),
        subtitle: t("mappingRuleCreatedSuccessfully", {
          name: mappingRuleName,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      headline={t("createNewMappingRule")}
      open={open}
      onClose={onClose}
      loading={loading}
      error={error}
      submitDisabled={submitDisabled}
      confirmLabel={t("createMappingRule")}
      onSubmit={handleSubmit}
    >
      <TextField
        label={t("mappingRuleId")}
        placeholder={t("enterMappingRuleId")}
        onChange={setMappingRuleId}
        value={mappingRuleId}
        helperText={t("uniqueIdForMappingRule")}
        validate={(value) => value.trim().length > 0}
        errorMessage={t("mappingRuleIdRequired")}
        autoFocus
      />
      <TextField
        label={t("mappingRuleName")}
        placeholder={t("enterMappingRuleName")}
        onChange={setMappingRuleName}
        value={mappingRuleName}
        helperText={t("uniqueNameForMappingRule")}
        validate={(value) => value.trim().length > 0}
        errorMessage={t("mappingRuleNameRequired")}
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>{t("mappingRule")}</h3>
          <CustomStack orientation="horizontal">
            <TextField
              label={t("claimName")}
              placeholder={t("enterClaimName")}
              onChange={setClaimName}
              value={claimName}
              helperText={t("customClaimName")}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <TextField
              label={t("claimValue")}
              placeholder={t("enterClaimValue")}
              onChange={setClaimValue}
              value={claimValue}
              helperText={t("valueForClaim")}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};

export default AddMappingRuleModal;
