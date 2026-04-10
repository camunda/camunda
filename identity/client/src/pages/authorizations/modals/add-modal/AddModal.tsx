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
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { isOIDC, isTenantsApiEnabled } from "src/configuration";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import {
  ALL_RESOURCE_TYPES,
  createAuthorization,
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

const resourcePermissions: Record<ResourceType, PermissionType[]> = {
  AUDIT_LOG: ["READ"],
  AUTHORIZATION: ["CREATE", "DELETE", "READ", "UPDATE"],
  BATCH: [
    "CREATE",
    "CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE",
    "CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION",
    "CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE",
    "CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION",
    "CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE",
    "CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE",
    "CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE",
    "CREATE_BATCH_OPERATION_RESOLVE_INCIDENT",
    "READ",
    "UPDATE",
  ],
  COMPONENT: ["ACCESS"],
  DECISION_DEFINITION: [
    "CREATE_DECISION_INSTANCE",
    "DELETE_DECISION_INSTANCE",
    "READ_DECISION_DEFINITION",
    "READ_DECISION_INSTANCE",
  ],
  DECISION_REQUIREMENTS_DEFINITION: ["READ"],
  EXPRESSION: ["EVALUATE"],
  RESOURCE: [
    "CREATE",
    "READ",
    "DELETE_DRD",
    "DELETE_FORM",
    "DELETE_PROCESS",
    "DELETE_RESOURCE",
  ],
  GROUP: ["CREATE", "DELETE", "READ", "UPDATE"],
  MAPPING_RULE: ["CREATE", "DELETE", "READ", "UPDATE"],
  MESSAGE: ["CREATE", "READ"],
  PROCESS_DEFINITION: [
    "CANCEL_PROCESS_INSTANCE",
    "CLAIM_USER_TASK",
    "COMPLETE_USER_TASK",
    "CREATE_PROCESS_INSTANCE",
    "DELETE_PROCESS_INSTANCE",
    "MODIFY_PROCESS_INSTANCE",
    "READ_PROCESS_DEFINITION",
    "READ_PROCESS_INSTANCE",
    "READ_USER_TASK",
    "UPDATE_PROCESS_INSTANCE",
    "UPDATE_USER_TASK",
  ],
  USER_TASK: ["READ", "UPDATE", "CLAIM", "COMPLETE"],
  ROLE: ["CREATE", "DELETE", "READ", "UPDATE"],
  SYSTEM: ["READ", "READ_JOB_METRIC", "READ_USAGE_METRIC", "UPDATE"],
  TENANT: ["CREATE", "DELETE", "READ", "UPDATE"],
  USER: ["CREATE", "DELETE", "READ", "UPDATE"],
  CLUSTER_VARIABLE: ["CREATE", "UPDATE", "DELETE", "READ"],
  DOCUMENT: ["CREATE", "READ", "DELETE"],
  GLOBAL_LISTENER: [
    "CREATE_TASK_LISTENER",
    "DELETE_TASK_LISTENER",
    "READ_TASK_LISTENER",
    "UPDATE_TASK_LISTENER",
  ],
};

export const AddModal: FC<UseEntityModalProps<ResourceType>> = ({
  open,
  onClose,
  onSuccess,
  entity: defaultResourceType,
}) => {
  const { t, Translate } = useTranslate("authorizations");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, error }] = useApiCall<undefined, NewAuthorization>(
    createAuthorization,
    {
      suppressErrorNotification: true,
    },
  );

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

  const onSubmit = async (data: NewAuthorization) => {
    const { success } = await apiCall(data);

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("authorizationCreated"),
        subtitle: t("authorizationCreatedSuccess", {
          resourceType: data.resourceType,
        }),
      });
      onSuccess();
    }
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
      submitDisabled={loading}
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
            invalid={!!fieldState.error}
            invalidText={fieldState.error?.message}
          >
            {resourcePermissions[watchedResourceType].map((permission) => (
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
      resourceType: "USER",
      resourceId: "",
      resourcePropertyName: null,
      permissionTypes: [],
    };
  }
}
