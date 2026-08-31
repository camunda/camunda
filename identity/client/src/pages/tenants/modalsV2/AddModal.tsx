/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { Button, Heading, Text } from "@camunda/design-system";
import { ArrowRight, Info } from "lucide-react";
import { DocumentationLink } from "src/components/documentationV2";
import TextField from "src/components/formV2/TextField";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import Modal, { FormModal, UseModalProps } from "src/components/modalV2";
import { tenantMutations } from "src/utility/api/tenants/mutations";
import useTranslate from "src/utility/localization";
import { isValidTenantId } from "src/utility/validate";

type FormData = {
  name: string;
  tenantId: string;
  description: string;
};

const ITEM_TO_TAB: Record<string, string> = {
  assignUsers: "users",
  assignGroups: "groups",
  assignRoles: "roles",
  assignMappingRules: "mapping-rules",
  assignClients: "clients",
};

const BASE_ASSIGN_ENTITY_ITEMS = [
  "assignUsers",
  "assignGroups",
  "assignRoles",
] as const;

const OIDC_ASSIGN_ENTITY_ITEMS = [
  "assignMappingRules",
  "assignClients",
] as const;

type AddTenantModalProps = UseModalProps & {
  isOIDC: boolean;
};

const AddTenantModal: FC<AddTenantModalProps> = ({
  open,
  onClose,
  onSuccess,
  isOIDC,
}) => {
  const ASSIGN_ENTITY_ITEMS = isOIDC
    ? ([...BASE_ASSIGN_ENTITY_ITEMS, ...OIDC_ASSIGN_ENTITY_ITEMS] as const)
    : BASE_ASSIGN_ENTITY_ITEMS;
  const { t, Translate } = useTranslate("tenants");
  const navigate = useNavigate();
  const [createdTenant, setCreatedTenant] = useState<{
    name: string;
    tenantId: string;
  } | null>(null);
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(tenantMutations.create(qc));

  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      name: "",
      tenantId: "",
      description: "",
    },
    mode: "all",
  });

  const onSubmit = (data: FormData) => {
    mutate(
      {
        name: data.name,
        tenantId: data.tenantId,
        description: data.description,
      },
      {
        onSuccess: () => {
          setCreatedTenant({ name: data.name, tenantId: data.tenantId });
        },
      },
    );
  };

  if (createdTenant) {
    return (
      <Modal
        open={open}
        headline={t("tenantCreatedSuccessfully", { name: createdTenant.name })}
        onClose={onSuccess}
        hideCancelButton
        confirmLabel={t("gotIt")}
      >
        <div>
          <Heading
            as="h3"
            variant="heading-xs"
            className="border-b border-border pb-2"
          >
            {t("nextStepAssignEntities")}
          </Heading>
          <ul>
            {ASSIGN_ENTITY_ITEMS.map((item) => (
              <li key={item} className="border-b border-border py-1">
                <div className="flex items-center justify-between gap-2">
                  <Text>{t(item)}</Text>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={t(item)}
                    onClick={() => {
                      onClose?.();
                      void navigate(
                        `/tenants/${createdTenant.tenantId}/${ITEM_TO_TAB[item]}`,
                      );
                    }}
                  >
                    <ArrowRight aria-hidden="true" />
                  </Button>
                </div>
                {item === "assignClients" && (
                  <div className="flex items-start gap-2">
                    <Info
                      aria-hidden="true"
                      className="size-4 shrink-0 text-info-foreground-strong"
                    />
                    <Text as="p" variant="label-sm">
                      <span>{t("assignConnectorRoleInfo")}</span>
                      <span>
                        <Translate i18nKey="dynamicAccessToAssignedTenantsInfoLink">
                          Your clients can be configured to
                          <DocumentationLink path="/components/admin/tenant/">
                            dynamically access assigned tenants
                          </DocumentationLink>
                          .
                        </Translate>
                      </span>
                    </Text>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      </Modal>
    );
  }

  return (
    <FormModal
      open={open}
      headline={t("createNewTenant")}
      loading={loading}
      error={error}
      loadingDescription={t("creatingTenant")}
      confirmLabel={t("createTenant")}
      onClose={onClose}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Controller
        name="tenantId"
        control={control}
        rules={{
          validate: (value) =>
            isValidTenantId(value) || t("pleaseEnterValidTenantId"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("tenantId")}
            placeholder={t("tenantIdPlaceholder")}
            errors={fieldState.error?.message}
            helperText={t("tenantIdHelperText")}
            autoFocus
          />
        )}
      />
      <Controller
        name="name"
        control={control}
        rules={{
          required: t("tenantNameRequired"),
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("tenantName")}
            placeholder={t("tenantNamePlaceholder")}
            errors={fieldState.error?.message}
          />
        )}
      />
      <Controller
        name="description"
        control={control}
        render={({ field }) => (
          <TextField
            {...field}
            label={t("description")}
            placeholder={t("tenantDescriptionPlaceholder")}
            cols={2}
            enableCounter
          />
        )}
      />
    </FormModal>
  );
};

export default AddTenantModal;
