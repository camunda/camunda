/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useForm } from "@tanstack/react-form";
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
} from "../components";
import { spacing05 } from "@carbon/elements";
import { Stack } from "@carbon/react";

type FormData = {
  mappingRuleId: string;
  mappingRuleName: string;
  claimName: string;
  claimValue: string;
};

export const AddMappingRuleModal: FC<UseModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, error }] = useApiCall(createMappingRule, {
    suppressErrorNotification: true,
  });

  const form = useForm({
    defaultValues: {
      mappingRuleId: "",
      mappingRuleName: "",
      claimName: "",
      claimValue: "",
    } as FormData,
    onSubmit: async ({ value }) => {
      const { success } = await apiCall({
        mappingRuleId: value.mappingRuleId,
        name: value.mappingRuleName,
        claimName: value.claimName,
        claimValue: value.claimValue,
      });

      if (success) {
        enqueueNotification({
          kind: "success",
          title: t("mappingRuleCreated"),
          subtitle: t("mappingRuleCreatedSuccessfully", {
            name: value.mappingRuleName,
          }),
        });
        onSuccess();
      }
    },
  });

  const validateRequired =
    (errorMessage: string) =>
    ({ value }: { value: string }) => {
      if (!value || value.trim() === "") {
        return errorMessage;
      }
    };

  const submitDisabled = loading || form.state.isSubmitting;

  return (
    <FormModal
      headline={t("createNewMappingRule")}
      open={open}
      onClose={onClose}
      loading={loading}
      error={error}
      submitDisabled={submitDisabled}
      confirmLabel={t("createMappingRule")}
      onSubmit={form.handleSubmit}
    >
      <form.Field
        name="mappingRuleId"
        validators={{
          onChange: validateRequired(t("mappingRuleIdRequired")),
          onBlur: validateRequired(t("mappingRuleIdRequired")),
        }}
        children={(field) => (
          <TextField
            label={t("mappingRuleId")}
            placeholder={t("enterMappingRuleId")}
            helperText={t("uniqueIdForMappingRule")}
            value={field.state.value}
            onChange={(value) => field.handleChange(value)}
            onBlur={field.handleBlur}
            errors={
              [...new Set(field.state.meta.errors)].filter(Boolean) as string[]
            }
            autoFocus
          />
        )}
      />
      <form.Field
        name="mappingRuleName"
        validators={{
          onChange: validateRequired(t("mappingRuleNameRequired")),
          onBlur: validateRequired(t("mappingRuleNameRequired")),
        }}
        children={(field) => (
          <TextField
            label={t("mappingRuleName")}
            placeholder={t("enterMappingRuleName")}
            helperText={t("uniqueNameForMappingRule")}
            value={field.state.value}
            onChange={(value) => field.handleChange(value)}
            onBlur={() => field.handleBlur()}
            errors={
              [...new Set(field.state.meta.errors)].filter(Boolean) as string[]
            }
          />
        )}
      />
      <MappingRuleContainer>
        <Stack gap={spacing05}>
          <h3>{t("mappingRule")}</h3>
          <CustomStack orientation="horizontal">
            <form.Field
              name="claimName"
              validators={{
                onChange: validateRequired(t("claimNameRequired")),
                onBlur: validateRequired(t("claimNameRequired")),
              }}
              children={(field) => (
                <TextField
                  label={t("claimName")}
                  placeholder={t("enterClaimName")}
                  helperText={t("customClaimName")}
                  value={field.state.value}
                  onChange={(value) => field.handleChange(value)}
                  onBlur={() => field.handleBlur()}
                  errors={
                    [...new Set(field.state.meta.errors)].filter(
                      Boolean,
                    ) as string[]
                  }
                />
              )}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <form.Field
              name="claimValue"
              validators={{
                onChange: validateRequired(t("claimValueRequired")),
                onBlur: validateRequired(t("claimValueRequired")),
              }}
              children={(field) => (
                <TextField
                  label={t("claimValue")}
                  placeholder={t("enterClaimValue")}
                  helperText={t("valueForClaim")}
                  value={field.state.value}
                  onChange={(value) => field.handleChange(value)}
                  onBlur={() => field.handleBlur()}
                  errors={
                    [...new Set(field.state.meta.errors)].filter(
                      Boolean,
                    ) as string[]
                  }
                />
              )}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};
