/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useId } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  Checkbox,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
  Text,
} from "@camunda/design-system";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { authorizationMutations } from "src/utility/api/authorizations/mutations";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalCustomProps } from "src/components/modalV2";
import {
  ALL_RESOURCE_TYPES,
  NewAuthorization,
  RESOURCE_TYPES_WITHOUT_TENANT,
  OWNER_TYPES,
  RESOURCE_PROPERTY_NAMES,
  type ResourcePropertyName,
} from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";
import TextField from "src/components/formV2/TextField";
import FormField from "src/components/formV2/FormField";
import { DocumentationLink } from "src/components/documentationV2";
import OwnerSelection from "../owner-selection";
import {
  isValidId,
  isValidResourceId,
  getIdPattern,
} from "src/utility/validate";
import type {
  OwnerType,
  PermissionType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.10";

type AddModalCustomProps = {
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  isTenantsApiEnabled: boolean;
  resourcePermissions: Record<ResourceType, PermissionType[]>;
};

export const AddModal: FC<
  UseEntityModalCustomProps<ResourceType, AddModalCustomProps>
> = ({
  open,
  onClose,
  onSuccess,
  entity: defaultResourceType,
  isOIDC,
  isCamundaGroupsEnabled,
  isTenantsApiEnabled,
  resourcePermissions,
}) => {
  const { t, Translate } = useTranslate("authorizations");
  const permissionsLegendId = useId();
  const { enqueueNotification } = useNotifications();
  const qc = useQueryClient();
  const {
    mutate,
    isPending: loading,
    error,
  } = useMutation(authorizationMutations.create(qc));

  const resourceTypeItems: ResourceType[] = isTenantsApiEnabled
    ? ALL_RESOURCE_TYPES
    : RESOURCE_TYPES_WITHOUT_TENANT;

  const ownerTypeItems: OwnerType[] = OWNER_TYPES.filter((ownerType) => {
    const excludedType = isOIDC
      ? ["UNSPECIFIED"]
      : ["MAPPING_RULE", "CLIENT", "UNSPECIFIED"];

    return !excludedType.includes(ownerType);
  });

  const { control, handleSubmit, watch, setValue } = useForm<NewAuthorization>({
    defaultValues: createEmptyAuthorization(defaultResourceType),
    mode: "all",
  });

  const watchedOwnerType = watch("ownerType");
  const watchedResourceType = watch("resourceType");
  const permissionsForType = resourcePermissions[watchedResourceType] ?? [];
  const hasPermissions = permissionsForType.length > 0;

  const onSubmit = (data: NewAuthorization) => {
    mutate(data, {
      onSuccess: () => {
        enqueueNotification({
          kind: "success",
          title: t("authorizationCreated"),
          subtitle: t("authorizationCreatedSuccess", {
            resourceType: data.resourceType,
          }),
        });
        onSuccess();
      },
    });
  };

  useEffect(() => {
    setValue("permissionTypes", []);
    if (watchedResourceType !== "USER_TASK") {
      setValue("resourceId", "");
    } else {
      setValue("resourcePropertyName", "assignee");
    }
  }, [watchedResourceType, setValue]);

  return (
    <FormModal
      headline={t("createAuthorization")}
      open={open}
      onClose={onClose}
      loading={loading}
      error={error}
      submitDisabled={loading || !hasPermissions}
      confirmLabel={t("createAuthorization")}
      onSubmit={handleSubmit(onSubmit)}
    >
      <Text as="p">
        <Translate i18nKey="createAuthorizationIntroduction">
          Grant an owner access to a resource with specific permissions.{" "}
          <DocumentationLink path="/components/admin/authorization/" withIcon>
            Learn more
          </DocumentationLink>{" "}
          .
        </Translate>
      </Text>
      <div className="grid w-full grid-cols-[1fr_2fr] items-start justify-center gap-4 gap-y-6">
        <Controller
          name="ownerType"
          control={control}
          render={({ field }) => (
            <FormField label={t("ownerType")}>
              {({ id }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => {
                    setValue("ownerId", "");
                    field.onChange(value as OwnerType);
                  }}
                >
                  <SelectTrigger id={id} className="w-full" autoFocus>
                    <SelectValue placeholder={t("selectOwnerType")} />
                  </SelectTrigger>
                  <SelectContent>
                    {ownerTypeItems.map((ownerType) => (
                      <SelectItem key={ownerType} value={ownerType}>
                        {t(ownerType)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </FormField>
          )}
        />
        <Controller
          name="ownerId"
          control={control}
          rules={{
            required: "EMPTY",
            validate: (value) => isValidId(value),
          }}
          render={({ field, fieldState }) => (
            <OwnerSelection
              type={watchedOwnerType}
              ownerId={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              isEmpty={fieldState.error?.type === "required"}
              isInvalidId={fieldState.error?.type === "validate"}
              isOIDC={isOIDC}
              isCamundaGroupsEnabled={isCamundaGroupsEnabled}
            />
          )}
        />

        <Separator className="col-span-full" />

        <Controller
          name="resourceType"
          control={control}
          render={({ field }) => (
            <FormField label={t("resourceType")}>
              {({ id }) => (
                <Select
                  disabled
                  value={
                    resourceTypeItems.find((item) => item === field.value) ||
                    resourceTypeItems[0]
                  }
                  onValueChange={(value) =>
                    field.onChange(value as ResourceType)
                  }
                >
                  <SelectTrigger id={id} className="w-full">
                    <SelectValue placeholder={t("selectResourceType")} />
                  </SelectTrigger>
                  <SelectContent>
                    {resourceTypeItems.map((resourceType) => (
                      <SelectItem key={resourceType} value={resourceType}>
                        {t(resourceType)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </FormField>
          )}
        />
        {watchedResourceType === "USER_TASK" ? (
          <Controller
            name="resourcePropertyName"
            control={control}
            render={({ field, fieldState }) => (
              <FormField
                label={t("resourcePropertyName")}
                error={fieldState.error?.message}
              >
                {({ id, ...controlProps }) => (
                  <Select
                    value={field.value ?? ""}
                    onValueChange={(value) =>
                      field.onChange(value as ResourcePropertyName)
                    }
                  >
                    <SelectTrigger id={id} className="w-full" {...controlProps}>
                      <SelectValue
                        placeholder={t("selectResourcePropertyName")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {RESOURCE_PROPERTY_NAMES.map((name) => (
                        <SelectItem key={name} value={name}>
                          {name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </FormField>
            )}
          />
        ) : (
          <Controller
            name="resourceId"
            control={control}
            rules={{
              required: t("resourceIdRequired"),
              validate: (value) =>
                isValidResourceId(value ?? "") ||
                t("pleaseEnterValidResourceId", {
                  pattern: getIdPattern(),
                }),
            }}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                value={field.value ?? ""}
                label={t("resourceId")}
                placeholder={t("enterId")}
                errors={fieldState.error?.message}
              />
            )}
          />
        )}
      </div>
      <Separator />
      <Controller
        name="permissionTypes"
        control={control}
        rules={{
          required: t("permissionRequired"),
        }}
        render={({ field, fieldState }) => {
          const showError = !hasPermissions || !!fieldState.error;

          return (
            <div
              role="group"
              aria-labelledby={permissionsLegendId}
              className="flex flex-col gap-1.5 pb-2"
            >
              <Text as="span" id={permissionsLegendId} variant="helper">
                <Translate i18nKey="selectPermission">
                  Select at least one permission. All available resource
                  permissions can be found{" "}
                  <DocumentationLink
                    path="/components/concepts/access-control/authorizations/#available-resources"
                    withIcon
                  >
                    here
                  </DocumentationLink>{" "}
                  .
                </Translate>
              </Text>
              <div className="flex flex-col gap-2">
                {permissionsForType.map((permission) => (
                  <div key={permission} className="flex items-center gap-2">
                    <Checkbox
                      id={permission}
                      checked={field.value.includes(permission)}
                      aria-invalid={showError}
                      onCheckedChange={(checked) => {
                        const currentPermissions = field.value;
                        const newPermissions =
                          checked === true
                            ? [...currentPermissions, permission]
                            : currentPermissions.filter(
                                (p) => p !== permission,
                              );
                        field.onChange(newPermissions);
                      }}
                      onBlur={field.onBlur}
                    />
                    <Label htmlFor={permission}>{permission}</Label>
                  </div>
                ))}
              </div>
              {showError ? (
                <Text
                  as="p"
                  variant="helper"
                  role="alert"
                  className="text-danger-action-default"
                >
                  {!hasPermissions
                    ? t("permissionsUnavailable")
                    : fieldState.error?.message}
                </Text>
              ) : null}
            </div>
          );
        }}
      />
    </FormModal>
  );
};

function createEmptyAuthorization(
  resourceType: ResourceType,
): NewAuthorization {
  if (resourceType === "USER_TASK") {
    return {
      ownerType: "USER",
      ownerId: "",
      resourceType: "USER_TASK",
      resourceId: null,
      resourcePropertyName: "assignee",
      permissionTypes: [],
    };
  } else {
    return {
      ownerType: "USER",
      ownerId: "",
      resourceType,
      resourceId: "",
      resourcePropertyName: null,
      permissionTypes: [],
    };
  }
}
