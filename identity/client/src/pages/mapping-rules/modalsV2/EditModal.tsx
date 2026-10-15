/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Controller, useForm } from "react-hook-form";
import { Heading, Separator } from "@camunda/design-system";
import TextField from "src/components/formV2/TextField";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modalV2";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { mappingRuleMutations } from "src/utility/api/mapping-rules/mutations";
import { useNotifications } from "src/components/notifications";
import type {
  MappingRuleType,
  TypedMappingRule,
} from "src/utility/api/mapping-rules";
import MappingRuleTypeSelect from "./MappingRuleTypeSelect";

const EditModal: FC<UseEntityModalProps<TypedMappingRule>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate("mappingRules");
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(mappingRuleMutations.update(qc));
  type FormData = Pick<
    TypedMappingRule,
    "mappingRuleId" | "name" | "claimName" | "claimValue"
  > & { type: MappingRuleType };
  const { control, handleSubmit, watch } = useForm<FormData>({
    defaultValues: {
      mappingRuleId: entity.mappingRuleId,
      name: entity.name,
      claimName: entity.claimName,
      claimValue: entity.claimValue,
      type: entity.type ?? "TOKEN_CLAIM",
    },
    mode: "all",
  });

  const isAttributeRule = watch("type") !== "TOKEN_CLAIM";

  const onSubmit = (data: FormData) => {
    mutate(
      {
        mappingRuleId: data.mappingRuleId,
        name: data.name,
        claimName: data.claimName,
        claimValue: data.claimValue,
        type: data.type,
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("mappingRuleUpdated"),
            subtitle: t("mappingRuleUpdatedSuccessfully", { name: data.name }),
          });
          onSuccess();
        },
      },
    );
  };

  return (
    <FormModal
      open={open}
      headline={t("editMappingRule")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      error={error}
      loadingDescription={t("updatingMappingRule")}
      confirmLabel={t("updateMappingRule")}
    >
      <Controller
        name="mappingRuleId"
        control={control}
        render={({ field }) => (
          <TextField {...field} label={t("mappingRuleId")} readOnly />
        )}
      />
      <Controller
        name="name"
        control={control}
        rules={{ required: t("mappingRuleNameRequired") }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("mappingRuleName")}
            placeholder={t("enterMappingRuleName")}
            helperText={t("uniqueNameForMappingRule")}
            errors={fieldState.error?.message}
            autoFocus
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
      <Separator />
      <div className="grid gap-2">
        <Heading as="h3" variant="heading-xs">
          {t("mappingRule")}
        </Heading>
        <div className="grid gap-2 grid-cols-[1fr_auto_1fr]">
          <Controller
            name="claimName"
            control={control}
            rules={{ required: t("claimNameRequired") }}
            render={({ field, fieldState }) => (
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
                errors={fieldState.error?.message}
              />
            )}
          />
          <div className="flex items-center justify-center self-center text-lg">
            =
          </div>
          <Controller
            name="claimValue"
            control={control}
            rules={{ required: t("claimValueRequired") }}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                label={t("claimValue")}
                placeholder={t("enterClaimValue")}
                helperText={t("valueForClaim")}
                errors={fieldState.error?.message}
              />
            )}
          />
        </div>
      </div>
    </FormModal>
  );
};

export default EditModal;
