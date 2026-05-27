/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { Checkbox, CheckboxGroup, Dropdown } from "@carbon/react";
import { useCreateAuthorization } from "src/utility/api/authorizations/hooks";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalCustomProps } from "src/components/modal";
import {
  ALL_RESOURCE_TYPES,
  NewAuthorization,
  RESOURCE_TYPES_WITHOUT_TENANT,
  OWNER_TYPES,
  RESOURCE_PROPERTY_NAMES,
  type ResourcePropertyName,
} from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";
import TextField from "src/components/form/TextField";
import Divider from "src/components/form/Divider";
import { DocumentationLink } from "src/components/documentation";
import { Caption, Row, TextFieldContainer } from "../components";
import OwnerSelection from "../owner-selection";
import { useDropdownAutoFocus } from "./useDropdownAutoFocus";
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
  const { enqueueNotification } = useNotifications();
  const { mutate, isPending: loading, error } = useCreateAuthorization();

  const { DropdownAutoFocus } = useDropdownAutoFocus(open);

  const resourceTypeItems: ResourceType[] = isTenantsApiEnabled
    ? ALL_RESOURCE_TYPES
    : RESOURCE_TYPES_WITHOUT_TENANT;

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
      <div>
        <Translate i18nKey="createAuthorizationIntroduction">
          Grant an owner access to a resource with specific permissions.{" "}
          <DocumentationLink path="/components/admin/authorization/" withIcon>
            Learn more
          </DocumentationLink>{" "}
          .
        </Translate>
      </div>
      <Row>
        <DropdownAutoFocus>
          <Controller
            name="ownerType"
            control={control}
            render={({ field }) => (
              <Dropdown<OwnerType>
                id="owner-type-dropdown"
                label={t("selectOwnerType")}
                titleText={t("ownerType")}
                items={OWNER_TYPES.filter((ownerType) => {
                  const excludedType = isOIDC
                    ? ["UNSPECIFIED"]
                    : ["MAPPING_RULE", "CLIENT", "UNSPECIFIED"];

                  return !excludedType.includes(ownerType);
                })}
                onChange={(item) => {
                  setValue("ownerId", "");
                  field.onChange(item.selectedItem);
                }}
                itemToString={(item) => (item ? t(item) : "")}
                selectedItem={field.value}
              />
            )}
          />
        </DropdownAutoFocus>
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
      </Row>
      <Divider />
      <Row>
        <Controller
          name="resourceType"
          control={control}
          render={({ field }) => (
            <Dropdown<ResourceType>
              id="resource-type-dropdown"
              disabled
              label={t("selectResourceType")}
              titleText={t("resourceType")}
              items={resourceTypeItems}
              onChange={(item) => {
                field.onChange(item.selectedItem);
              }}
              itemToString={(item) => (item ? t(item) : "")}
              selectedItem={
                resourceTypeItems.find((item) => item === field.value) ||
                resourceTypeItems[0]
              }
            />
          )}
        />
        <TextFieldContainer>
          {watchedResourceType === "USER_TASK" ? (
            <Controller
              name="resourcePropertyName"
              control={control}
              render={({ field, fieldState }) => {
                return (
                  <Dropdown<ResourcePropertyName>
                    id="property-name-dropdown"
                    label={t("selectResourcePropertyName")}
                    titleText={t("resourcePropertyName")}
                    items={RESOURCE_PROPERTY_NAMES}
                    onChange={(item) => {
                      field.onChange(item.selectedItem);
                    }}
                    itemToString={(item) => item || ""}
                    selectedItem={field.value}
                    invalid={!!fieldState.error}
                    invalidText={fieldState.error?.message}
                  />
                );
              }}
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
        </TextFieldContainer>
      </Row>
      <Divider />
      <Controller
        name="permissionTypes"
        control={control}
        rules={{
          required: t("permissionRequired"),
        }}
        render={({ field, fieldState }) => (
          <CheckboxGroup
            legendText={
              <Caption>
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
              </Caption>
            }
            invalid={!hasPermissions || !!fieldState.error}
            invalidText={
              !hasPermissions
                ? t("permissionsUnavailable")
                : fieldState.error?.message
            }
          >
            {permissionsForType.map((permission) => (
              <Checkbox
                key={permission}
                labelText={permission}
                id={permission}
                checked={field.value.includes(permission)}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const currentPermissions = field.value;
                  const newPermissions = e.target.checked
                    ? [...currentPermissions, permission]
                    : currentPermissions.filter((p) => p !== permission);
                  field.onChange(newPermissions);
                }}
                onBlur={field.onBlur}
              />
            ))}
          </CheckboxGroup>
        )}
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
