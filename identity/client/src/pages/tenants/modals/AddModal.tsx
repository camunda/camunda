/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  Button, InlineNotification,
  Link,
  Stack,
  StructuredListBody,
  StructuredListCell,
  StructuredListRow,
  StructuredListWrapper,
} from "@carbon/react";
import { spacing06 } from "@carbon/elements";
import styled from "styled-components";
import { documentationHref } from "src/components/documentation";
import TextField from "src/components/form/TextField";
import Modal, { FormModal, UseModalProps } from "src/components/modal";
import { docsUrl } from "src/configuration";
import { useApiCall } from "src/utility/api";
import { createTenant } from "src/utility/api/tenants";
import useTranslate from "src/utility/localization";
import { isValidTenantId } from "src/utility/validate";

type FormData = {
  name: string;
  tenantId: string;
  description: string;
};

const StyledStructuredListWrapper = styled(StructuredListWrapper)`
  margin-bottom: 0;
`;

const RightAlignedButtonSet = styled.div`
  display: flex;
  justify-content: flex-end;
  width: 100%;
  gap: ${spacing06};
`;

const ASSIGN_ENTITY_ITEMS = [
  "assignUsers",
  "assignGroups",
  "assignRoles",
  "assignMappingRules",
  "assignClients",
] as const;

const AddTenantModal: FC<UseModalProps> = ({ open, onClose, onSuccess }) => {
  const { t, Translate } = useTranslate("tenants");
  const [createdTenantName, setCreatedTenantName] = useState<string | null>(
    null,
  );
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
      setCreatedTenantName(data.name);
    }
  };

  if (createdTenantName) {
    return (
      <Modal
        open={open}
        headline={t("tenantCreatedSuccessfully", { name: createdTenantName })}
        confirmLabel={t("close")}
        onClose={onSuccess}
        buttons={[
          <RightAlignedButtonSet key="close">
            <Button kind="secondary" onClick={onSuccess}>
              {t("close")}
            </Button>
          </RightAlignedButtonSet>,
        ]}
      >
        <Stack gap={spacing06}>
          <strong>{t("nextStepAssignEntities")}</strong>
          <StyledStructuredListWrapper>
            <StructuredListBody>
              {ASSIGN_ENTITY_ITEMS.map((item) => (
                <StructuredListRow key={item}>
                  <StructuredListCell>
                    {t(item)}
                    {item === "assignClients" && (
                      <InlineNotification
                        kind="info"
                        lowContrast
                        hideCloseButton
                        style={{ marginTop: spacing06 }}
                      >
                        t("assignConnectorRoleInfo", {link: "blah"})
                        <Translate i18nKey="assignConnectorRoleInfo">
                          Assign the &ldquo;connector&rdquo; role to
                          automatically assign all clients to this tenant. Your
                          clients can be configured to
                          <Link
                            href={documentationHref(
                              docsUrl,
                              "/docs/components/identity/tenant/",
                            )}
                            target="_blank"
                            inline
                          >
                            dynamically access assigned tenants
                          </Link>
                          .
                        </Translate>
                      </InlineNotification>
                    )}
                  </StructuredListCell>
                </StructuredListRow>
              ))}
            </StructuredListBody>
          </StyledStructuredListWrapper>
        </Stack>
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
