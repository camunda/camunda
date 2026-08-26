/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useId } from "react";
import type { ClusterVariableScope } from "@camunda/camunda-api-zod-schemas/8.10";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { clusterVariableMutations } from "src/utility/api/cluster-variables/mutations";
import { Label, RadioGroup, RadioGroupItem } from "@camunda/design-system";
import { Controller, useForm } from "react-hook-form";
import { useNotifications } from "src/components/notifications";
import { FormModal, UseModalProps } from "src/components/modalV2";
import useTranslate from "src/utility/localization";
import TextField from "src/components/formV2/TextField.tsx";
import JSONEditor from "src/components/formV2/JSONEditor.tsx";
import { isValid } from "src/utility/components/editor/jsonUtils.ts";
import ClusterVariableTenantDropdown from "./ClusterVariableTenantDropdown.tsx";

type FormData = {
  name: string;
  value: string;
  scope: ClusterVariableScope;
  tenantId?: string;
};

type AddModalProps = UseModalProps & { isSaaS: boolean };

export const AddModal: FC<AddModalProps> = ({
  open,
  onClose,
  onSuccess,
  isSaaS,
}) => {
  const { t } = useTranslate("clusterVariables");
  const scopeLabelId = useId();
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(clusterVariableMutations.create(qc));

  const { control, handleSubmit, watch } = useForm<FormData>({
    defaultValues: {
      name: "",
      value: "",
      scope: "GLOBAL",
      tenantId: "",
    },
    mode: "all",
  });

  const watchedScope = watch("scope");
  const isTenantScoped = watchedScope === "TENANT";

  const onSubmit = (data: FormData) => {
    mutate(
      {
        name: data.name.trim(),
        value: JSON.parse(data.value.trim()),
        scope: data.scope,
        tenantId: isTenantScoped ? (data.tenantId ?? null) : "",
      },
      {
        onSuccess: () => {
          enqueueNotification({
            kind: "success",
            title: t("clusterVariableCreated"),
            subtitle: t("clusterVariableCreatedSuccessfully", {
              clusterVariableName: data.name,
            }),
          });
          onSuccess();
        },
      },
    );
  };

  return (
    <FormModal
      open={open}
      headline={t("createClusterVariable")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
      loading={loading}
      error={error}
      loadingDescription={t("creatingClusterVariable")}
      confirmLabel={t("createClusterVariable")}
    >
      <Controller
        name="name"
        control={control}
        rules={{
          required: t("clusterVariableNameRequired"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("name")}
            placeholder={t("clusterVariableNamePlaceholder")}
            errors={fieldState.error?.message}
            autoFocus
          />
        )}
      />
      <Controller
        name="scope"
        control={control}
        render={({ field }) => (
          <div className="flex flex-col gap-1.5">
            <Label id={scopeLabelId}>{t("scope")}</Label>
            <RadioGroup
              aria-labelledby={scopeLabelId}
              value={field.value}
              onValueChange={(value) =>
                field.onChange(value as ClusterVariableScope)
              }
              className="flex flex-row gap-4"
            >
              <Label className="flex items-center gap-2">
                <RadioGroupItem value="GLOBAL" />
                {t("clusterVariableScopeTypeGlobal")}
              </Label>
              <Label className="flex items-center gap-2">
                <RadioGroupItem value="TENANT" disabled={isSaaS} />
                {t("clusterVariableScopeTypeTenant")}
              </Label>
            </RadioGroup>
          </div>
        )}
      />
      {isTenantScoped && (
        <Controller
          name="tenantId"
          control={control}
          render={({ field }) => (
            <ClusterVariableTenantDropdown
              tenantId={field.value}
              onChange={(tenantId) => field.onChange(tenantId)}
            />
          )}
        />
      )}
      <Controller
        name="value"
        control={control}
        rules={{
          validate: (value) => {
            if (!value || !value.trim()) {
              return t("clusterVariableValueRequired");
            } else if (!isValid(value.trim())) {
              return t("clusterVariableValueInvalid");
            }

            return true;
          },
        }}
        render={({ field, fieldState }) => (
          <JSONEditor
            {...field}
            label={t("clusterVariableCreateValue")}
            errors={fieldState.error?.message}
            beautify
          />
        )}
      />
    </FormModal>
  );
};
