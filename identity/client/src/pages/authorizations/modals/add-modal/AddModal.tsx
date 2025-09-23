/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { Dropdown, CheckboxGroup, Checkbox } from "@carbon/react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { isOIDC, isTenantsApiEnabled } from "src/configuration";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import {
  Authorization,
  createAuthorization,
  OwnerType,
  PermissionType,
  ResourceType,
} from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";
import TextField from "src/components/form/TextField";
import Divider from "src/components/form/Divider";
import { DocumentationLink } from "src/components/documentation";
import {
  Row,
  TextFieldContainer,
  PermissionsSectionLabel,
} from "../components";
import OwnerSelection from "../owner-selection";
import { useDropdownAutoFocus } from "./useDropdownAutoFocus";

type ResourcePermissionsType = {
  [key in keyof typeof ResourceType]: Authorization["permissionTypes"];
};

const resourcePermissions: ResourcePermissionsType = {
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
  DECISION_REQUIREMENTS_DEFINITION: [
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  RESOURCE: [
    PermissionType.CREATE,
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
  ROLE: [
    PermissionType.CREATE,
    PermissionType.DELETE,
    PermissionType.READ,
    PermissionType.UPDATE,
  ],
  SYSTEM: [
    PermissionType.READ,
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
  DOCUMENT: [PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE],
};

type FormData = {
  ownerType: OwnerType;
  ownerId: string;
  resourceId: string;
  resourceType: ResourceType;
  permissionTypes: Authorization["permissionTypes"];
};

export const AddModal: FC<UseEntityModalProps<ResourceType>> = ({
  open,
  onClose,
  onSuccess,
  entity: defaultResourceType,
}) => {
  const { t, Translate } = useTranslate("authorizations");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, error }] = useApiCall(createAuthorization, {
    suppressErrorNotification: true,
  });

  const { DropdownAutoFocus } = useDropdownAutoFocus(open);

  const ownerTypeItems = Object.values(OwnerType);
  const allResourceTypes = Object.values(ResourceType);
  const resourceTypeItems = isTenantsApiEnabled
    ? allResourceTypes
    : allResourceTypes.filter((type) => type !== ResourceType.TENANT);

  const { control, handleSubmit, watch, setValue } = useForm<FormData>({
    defaultValues: {
      ownerType: OwnerType.USER,
      ownerId: "",
      resourceId: "",
      resourceType: defaultResourceType,
      permissionTypes: [],
    },
    mode: "all",
  });

  const watchedOwnerType = watch("ownerType");
  const watchedResourceType = watch("resourceType");

  const onSubmit = async (data: FormData) => {
    const { success } = await apiCall({
      ownerType: data.ownerType,
      ownerId: data.ownerId,
      resourceId: data.resourceId,
      resourceType: data.resourceType,
      permissionTypes: data.permissionTypes,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("authorizationCreated"),
        subtitle: t("authorizationCreatedSuccess", {
          resourceId: data.resourceId,
        }),
      });
      onSuccess();
    }
  };

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
      <Translate i18nKey="createAuthorizationIntroduction">
        Define the permissions granted to an owner for a specific resource.
      </Translate>
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
          }}
          render={({ field, fieldState }) => (
            <OwnerSelection
              type={watchedOwnerType}
              ownerId={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              isEmpty={!!fieldState.error?.message}
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
                setValue("permissionTypes", []);
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
          <Controller
            name="resourceId"
            control={control}
            rules={{
              required: t("resourceIdRequired"),
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
              <PermissionsSectionLabel>
                <Translate i18nKey="selectPermission">
                  Select at least one permission. For a full overview, see{" "}
                  <DocumentationLink
                    path="/docs/components/concepts/access-control/authorizations/#resources-and-permissions"
                    withIcon
                  >
                    documentation
                  </DocumentationLink>{" "}
                  .
                </Translate>
              </PermissionsSectionLabel>
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
