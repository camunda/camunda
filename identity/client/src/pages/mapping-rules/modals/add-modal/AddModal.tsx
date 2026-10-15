/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import TextField from "src/components/form/TextField";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { useNotifications } from "src/components/notifications";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { mappingRuleMutations } from "src/utility/api/mapping-rules/mutations";
import {
  CustomStack,
  EqualSignContainer,
  MappingRuleContainer,
} from "../components";
import { Stack } from "@carbon/react";
import { isValidId, getIdPattern } from "src/utility/validate";
import type { MappingRuleType } from "src/utility/api/mapping-rules";
import MappingRuleTypeSelect from "../MappingRuleTypeSelect";

type FormData = {
  mappingRuleId: string;
  mappingRuleName: string;
  claimName: string;
  claimValue: string;
  type: MappingRuleType;
};

export const AddMappingRuleModal: FC<UseModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(mappingRuleMutations.create(qc));

  const {
    control,
    handleSubmit,
    formState: { errors },
    watch,
  } = useForm<FormData>({
    defaultValues: {
      mappingRuleId: "",
      mappingRuleName: "",
      claimName: "",
      claimValue: "",
      type: "TOKEN_CLAIM",
    },
    mode: "all",
  });

  const isAttributeRule = watch("type") !== "TOKEN_CLAIM";

  const onSubmit = (data: FormData) => {
    mutate(
      {
        mappingRuleId: data.mappingRuleId,
        name: data.mappingRuleName,
        claimName: data.claimName,
        claimValue: data.claimValue,
        type: data.type,
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("mappingRuleCreated"),
            subtitle: t("mappingRuleCreatedSuccessfully", {
              name: data.mappingRuleName,
            }),
          });
          onSuccess();
        },
      },
    );
  };

  return (
    <FormModal
      headline={t("createNewMappingRule")}
      open={open}
      onClose={onClose}
      loading={loading}
      error={error}
      confirmLabel={t("createMappingRule")}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="mappingRuleId"
        control={control}
        rules={{
          required: t("mappingRuleIdRequired"),
          validate: (value) =>
            isValidId(value) ||
            t("pleaseEnterValidMappingRuleId", {
              pattern: getIdPattern(),
            }),
        }}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("mappingRuleId")}
            placeholder={t("enterMappingRuleId")}
            helperText={t("uniqueIdForMappingRule")}
            errors={errors.mappingRuleId?.message}
            autoFocus
          />
        )}
      />
      <Controller
        name="mappingRuleName"
        control={control}
        rules={{
          required: t("mappingRuleNameRequired"),
        }}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("mappingRuleName")}
            placeholder={t("enterMappingRuleName")}
            helperText={t("uniqueNameForMappingRule")}
            errors={errors.mappingRuleName?.message}
          />
        )}
      />
      <Controller
        name="type"
        control={control}
        render={({ field }) => (
          <MappingRuleTypeSelect
            value={field.value}
            onChange={field.onChange}
          />
        )}
      />
      <MappingRuleContainer>
        <Stack gap="5">
          <h3>{t("mappingRule")}</h3>
          <CustomStack orientation="horizontal">
            <Controller
              name="claimName"
              control={control}
              rules={{
                required: t("claimNameRequired"),
              }}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={isAttributeRule ? t("attributePath") : t("claimName")}
                  placeholder={
                    isAttributeRule
                      ? t("enterAttributePath")
                      : t("enterClaimName")
                  }
                  helperText={
                    isAttributeRule
                      ? t("customAttributePath")
                      : t("customClaimName")
                  }
                  errors={errors.claimName?.message}
                />
              )}
            />
            <EqualSignContainer>=</EqualSignContainer>
            <Controller
              name="claimValue"
              control={control}
              rules={{
                required: t("claimValueRequired"),
              }}
              render={({ field }) => (
                <TextField
                  {...field}
                  label={t("claimValue")}
                  placeholder={t("enterClaimValue")}
                  helperText={t("valueForClaim")}
                  errors={errors.claimValue?.message}
                />
              )}
            />
          </CustomStack>
        </Stack>
      </MappingRuleContainer>
    </FormModal>
  );
};
