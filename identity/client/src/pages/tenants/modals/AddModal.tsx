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
import {
  Button,
  ContainedList,
  ContainedListItem,
  IconButton,
  Link,
  Stack,
} from "@carbon/react";
import { ArrowRight, InformationFilled } from "@carbon/react/icons";
import { spacing03, spacing06 } from "@carbon/elements";
import styled from "styled-components";
import { documentationHref } from "src/components/documentation";
import TextField from "src/components/form/TextField";
import Modal, { FormModal, UseModalProps } from "src/components/modal";
import { docsUrl, isOIDC } from "src/configuration";
import { useApiCall } from "src/utility/api";
import { createTenant } from "src/utility/api/tenants";
import useTranslate from "src/utility/localization";
import { isValidTenantId } from "src/utility/validate";

type FormData = {
  name: string;
  tenantId: string;
  description: string;
};

const RightAlignedButtonSet = styled.div`
  display: flex;
  justify-content: flex-end;
  width: 100%;
  gap: ${spacing06};
`;

const InfoHint = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${spacing03};
  margin-top: ${spacing03};
  color: var(--cds-text-primary);
  font-size: var(--cds-helper-text-01-font-size, 0.75rem);
  line-height: var(--cds-helper-text-01-line-height, 1.34);
`;

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

const ASSIGN_ENTITY_ITEMS = isOIDC
  ? ([...BASE_ASSIGN_ENTITY_ITEMS, ...OIDC_ASSIGN_ENTITY_ITEMS] as const)
  : BASE_ASSIGN_ENTITY_ITEMS;

const AddTenantModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t, Translate } = useTranslate("tenants");
  const navigate = useNavigate();
  const [createdTenant, setCreatedTenant] = useState<{
    name: string;
    tenantId: string;
  } | null>(null);
  const [callAddTenant, { loading, error }] = useApiCall(createTenant, {
    suppressErrorNotification: true,
  });

  const { control, handleSubmit } = useForm<FormData>({
    defaultValues: {
      name: "",
      tenantId: "",
      description: "",
    },
    mode: "all",
  });

  const onSubmit = async (data: FormData) => {
    const { success } = await callAddTenant({
      name: data.name,
      tenantId: data.tenantId,
      description: data.description,
    });

    if (success) {
      setCreatedTenant({ name: data.name, tenantId: data.tenantId });
    }
  };

  if (createdTenant) {
    return (
      <Modal
        open={open}
        headline={t("tenantCreatedSuccessfully", { name: createdTenant.name })}
        passiveModal
        onClose={onSuccess}
        buttons={[
          <RightAlignedButtonSet key="close">
            <Button kind="primary" onClick={onSuccess} data-modal-primary-focus>
              {t("gotIt")}
            </Button>
          </RightAlignedButtonSet>,
        ]}
      >
        <ContainedList label={t("nextStepAssignEntities")}>
          {ASSIGN_ENTITY_ITEMS.map((item) => (
            <ContainedListItem
              key={item}
              action={
                <IconButton
                  label={t(item)}
                  kind="ghost"
                  onClick={() => {
                    onClose?.();
                    navigate(
                      `/tenants/${createdTenant.tenantId}/${ITEM_TO_TAB[item]}`,
                    );
                  }}
                >
                  <ArrowRight />
                </IconButton>
              }
            >
              {t(item)}
              {item === "assignClients" && (
                <InfoHint>
                  <InformationFilled
                    size={16}
                    style={{ color: "var(--cds-support-info)", flexShrink: 0 }}
                  />
                  <div>
                    <div>{t("assignConnectorRoleInfo")}</div>
                    <div>
                      <Translate i18nKey="dynamicAccessToAssignedTenantsInfoLink">
                        Your clients can be configured to
                        <Link
                          href={documentationHref(
                            docsUrl,
                            "/docs/components/identity/tenant/",
                          )}
                          target="_blank"
                          inline
                          size="sm"
                        >
                          dynamically access assigned tenants
                        </Link>
                        .
                      </Translate>
                    </div>
                  </div>
                </InfoHint>
              )}
            </ContainedListItem>
          ))}
        </ContainedList>
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
      <Stack orientation="vertical" gap={spacing06}>
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
      </Stack>
    </FormModal>
  );
};

export default AddTenantModal;
