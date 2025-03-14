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
  const { t } = useTranslate("mappings");
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
      id: mappingId,
      name: mappingName,
      claimName,
      claimValue,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Mapping created"),
        subtitle: t("You have successfully created mapping {{ name }}", {
          name,
        }),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      headline={t("Create new mapping")}
      open={open}
      onClose={onClose}
      loading={loading}
      submitDisabled={submitDisabled}
      confirmLabel={t("Create mapping")}
      onSubmit={handleSubmit}
    >
      <TextField
        label={t("Mapping ID")}
        placeholder={t("Enter mapping ID")}
        onChange={setMappingId}
        value={mappingId}
        helperText={t("Enter a unique ID for this mapping")}
        autoFocus
      />
      <TextField
        label={t("Mapping name")}
        placeholder={t("Enter mapping name")}
        onChange={setMappingName}
        value={mappingName}
        helperText={t("Enter a unique name for this mapping")}
        autoFocus
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>Mapping Rule</h3>
          <CustomStack orientation="horizontal">
            <TextField
              label={t("Claim Name")}
              placeholder={t("Enter claim name")}
              onChange={setClaimName}
              value={claimName}
              helperText={t("Enter a custom claim name")}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <TextField
              label={t("Claim Value")}
              placeholder={t("Enter claim value")}
              onChange={setClaimValue}
              value={claimValue}
              helperText={t("Enter the value for the claim")}
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
