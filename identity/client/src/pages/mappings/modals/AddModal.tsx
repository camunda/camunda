/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import { InlineNotification } from "@carbon/react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { createMapping } from "src/utility/api/mappings";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "./components";
import { spacing05 } from "@carbon/elements";
import { Stack } from "@carbon/react";

const AddMappingModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, error }] = useApiCall(createMapping, {
    suppressErrorNotification: true,
  });
  const [mappingId, setMappingId] = useState("");
  const [mappingName, setMappingName] = useState("");
  const [claimName, setClaimName] = useState("");
  const [claimValue, setClaimValue] = useState("");

  const submitDisabled = loading || !mappingName;

  const handleSubmit = async () => {
    const { success } = await apiCall({
      mappingId: mappingId,
      name: mappingName,
      claimName,
      claimValue,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("mappingCreated"),
        subtitle: t("mappingCreatedSuccessfully", { name: mappingName }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      headline={t("createNewMapping")}
      open={open}
      onClose={onClose}
      loading={loading}
      submitDisabled={submitDisabled}
      confirmLabel={t("createMapping")}
      onSubmit={handleSubmit}
    >
      <TextField
        label={t("mappingId")}
        placeholder={t("enterMappingId")}
        onChange={setMappingId}
        value={mappingId}
        helperText={t("uniqueIdForMapping")}
        autoFocus
      />
      <TextField
        label={t("mappingName")}
        placeholder={t("enterMappingName")}
        onChange={setMappingName}
        value={mappingName}
        helperText={t("uniqueNameForMapping")}
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
      {error && (
        <InlineNotification
          kind="error"
          role="alert"
          lowContrast
          title={error.title}
          subtitle={error.detail}
        />
      )}
    </FormModal>
  );
};

export default AddMappingModal;
