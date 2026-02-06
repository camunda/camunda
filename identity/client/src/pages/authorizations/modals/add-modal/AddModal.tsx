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
  Authorization,
  createAuthorization,
  NewAuthorization,
  OwnerType,
  PermissionType,
  ResourcePropertyName,
  ResourceType,
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

type ResourcePermissionsType = {
  [key in keyof typeof ResourceType]: Authorization["permissionTypes"];
};

const resourcePermissions: ResourcePermissionsType = {
  AUDIT_LOG: [PermissionType.READ],
  AUTHORIZATION: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  BATCH: [
    PermissionType.CREATE,
    PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
    PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION,
    PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
    PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
    PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
    PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
    PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
    PermissionType.CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  COMPONENT: [PermissionType.ACCESS],
  DECISION_DEFINITION: [
    PermissionType.CREATE_DECISION_INSTANCE,
    PermissionType.DELETE_DECISION_INSTANCE,
    PermissionType.READ_DECISION_DEFINITION,
    PermissionType.READ_DECISION_INSTANCE,
  ],
  DECISION_REQUIREMENTS_DEFINITION: [PermissionType.READ],
  EXPRESSION: [PermissionType.EVALUATE],
  RESOURCE: [
    PermissionType.CREATE,
    PermissionType.READ,
    PermissionType.DELETE_DRD,
    PermissionType.DELETE_FORM,
    PermissionType.DELETE_PROCESS,
    PermissionType.DELETE_RESOURCE,
  ],
  GROUP: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  MAPPING_RULE: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  MESSAGE: [PermissionType.CREATE, PermissionType.READ],
  PROCESS_DEFINITION: [
    PermissionType.CANCEL_PROCESS_INSTANCE,
    PermissionType.CREATE_PROCESS_INSTANCE,
    PermissionType.DELETE_PROCESS_INSTANCE,
    PermissionType.MODIFY_PROCESS_INSTANCE,
    PermissionType.READ_PROCESS_DEFINITION,
    PermissionType.READ_PROCESS_INSTANCE,
    PermissionType.READ_USER_TASK,
    PermissionType.UPDATE_PROCESS_INSTANCE,
    PermissionType.UPDATE_USER_TASK,
  ],
  USER_TASK: [
    PermissionType.READ,
    PermissionType.UPDATE,
    PermissionType.CLAIM,
    PermissionType.COMPLETE,
  ],
  ROLE: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  SYSTEM: [
    PermissionType.READ,
    PermissionType.READ_JOB_METRIC,
    PermissionType.READ_USAGE_METRIC,
    PermissionType.UPDATE,
  ],
  TENANT: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  USER: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  CLUSTER_VARIABLE: [
    PermissionType.CREATE,
    PermissionType.UPDATE,
    PermissionType.DELETE,
    PermissionType.READ,
  ],
  DOCUMENT: [PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE],
  GLOBAL_LISTENER: [
    PermissionType.CREATE_TASK_LISTENER,
    PermissionType.DELETE_TASK_LISTENER,
    PermissionType.READ_TASK_LISTENER,
    PermissionType.UPDATE_TASK_LISTENER,
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

  const ownerTypeItems = Object.values(OwnerType);
  const allResourceTypes = Object.values(ResourceType);
  const userTaskResourcePropertyNames = Object.values(ResourcePropertyName);

  let resourceTypeItems = allResourceTypes;

  if (!isTenantsApiEnabled) {
    resourceTypeItems = resourceTypeItems.filter(
      (type) => type !== ResourceType.TENANT,
    );
  }

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
    if (watchedResourceType !== ResourceType.USER_TASK) {
      setValue("resourceId", "");
    } else {
      setValue("resourcePropertyName", ResourcePropertyName.assignee);
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
          <DocumentationLink
            path="/docs/components/identity/authorization/"
            withIcon
          >
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
              <Dropdown
                id="owner-type-dropdown"
                label={t("selectOwnerType")}
                titleText={t("ownerType")}
                items={ownerTypeItems.filter((ownerType) => {
                  const excludedType = isOIDC
                    ? []
                    : [OwnerType.MAPPING_RULE, OwnerType.CLIENT];

                  return !excludedType.includes(ownerType);
                })}
                onChange={(item: { selectedItem: OwnerType }) => {
                  setValue("ownerId", "");
                  field.onChange(item.selectedItem);
                }}
                itemToString={(item: Authorization["ownerType"]) =>
                  item ? t(OwnerType[item]) : ""
                }
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
            <Dropdown
              id="resource-type-dropdown"
              label={t("selectResourceType")}
              titleText={t("resourceType")}
              items={resourceTypeItems}
              onChange={(item: { selectedItem: ResourceType }) => {
                field.onChange(item.selectedItem);
              }}
              itemToString={(item: string) => (item ? t(item) : "")}
              selectedItem={
                resourceTypeItems.find((item) => item === field.value) || ""
              }
            />
          )}
        />
        <TextFieldContainer>
          {watchedResourceType === ResourceType.USER_TASK ? (
            <Controller
              name="resourcePropertyName"
              control={control}
              render={({ field, fieldState }) => {
                return (
                  <Dropdown
                    id="property-name-dropdown"
                    label={t("selectResourcePropertyName")}
                    titleText={t("resourcePropertyName")}
                    items={userTaskResourcePropertyNames}
                    onChange={(item: { selectedItem: string }) => {
                      field.onChange(item.selectedItem);
                    }}
                    itemToString={(item: string) => item || ""}
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
                  isValidResourceId(value) ||
                  t("pleaseEnterValidResourceId", {
                    pattern: getIdPattern(),
                  }),
              }}
              render={({ field, fieldState }) => (
                <TextField
                  {...field}
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
                    path="/docs/components/concepts/access-control/authorizations/#resources-and-permissions"
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
                labelText={PermissionType[permission]}
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
  if (resourceType === ResourceType.USER_TASK) {
    return {
      ownerType: OwnerType.USER,
      ownerId: "",
      resourceType: ResourceType.USER_TASK,
      resourcePropertyName: ResourcePropertyName.assignee,
      permissionTypes: [],
    };
  } else {
    return {
      ownerType: OwnerType.USER,
      ownerId: "",
      resourceType: ResourceType.USER,
      resourceId: "",
      permissionTypes: [],
    };
  }
}
