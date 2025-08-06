/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
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
  PermissionTypes,
  ResourceType,
} from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";
import TextField from "src/components/form/TextField";
import Divider from "src/components/form/Divider";
import { DocumentationLink } from "src/components/documentation";
import { Row, TextFieldContainer, PermissionsSectionLabel } from "./components";
import OwnerSelection from "./OwnerSelection";

type ResourcePermissionsType = {
  [key in keyof typeof ResourceType]: Authorization["permissionTypes"];
};

const resourcePermissions: ResourcePermissionsType = {
  APPLICATION: [PermissionType.ACCESS],
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
    PermissionType.CREATE_PROCESS_INSTANCE,
    PermissionType.DELETE_PROCESS_INSTANCE,
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
  SYSTEM: [PermissionType.READ, PermissionType.UPDATE],
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
};

const AddModal: FC<UseEntityModalProps<ResourceType>> = ({
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
  const [ownerType, setOwnerType] = useState<OwnerType>(OwnerType.USER);
  const [ownerId, setOwnerId] = useState("");
  const [resourceId, setResourceId] = useState("");
  const [permissionTypes, setPermissionTypes] = useState<
    Authorization["permissionTypes"]
  >([]);

  const ownerTypeItems = Object.values(OwnerType);
  const allResourceTypes = Object.values(ResourceType);
  const resourceTypeItems = isTenantsApiEnabled
    ? allResourceTypes
    : allResourceTypes.filter((type) => type !== ResourceType.TENANT);

  const [resourceType, setResourceType] =
    useState<ResourceType>(defaultResourceType);

  const handleChangeCheckbox = (checked: boolean, id: PermissionTypes) => {
    if (checked) {
      setPermissionTypes([...permissionTypes, id]);
    } else {
      setPermissionTypes(permissionTypes.filter((p) => p !== id));
    }
  };

  const handleSubmit = async () => {
    const { success } = await apiCall({
      ownerType,
      ownerId,
      resourceId,
      resourceType,
      permissionTypes,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("authorizationCreated"),
        subtitle: t("authorizationCreatedSuccess", {
          resourceId,
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
      onSubmit={handleSubmit}
    >
      <Row>
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
            setOwnerId("");
            setOwnerType(item.selectedItem);
          }}
          itemToString={(item: Authorization["ownerType"]) =>
            item ? t(OwnerType[item]) : ""
          }
          selectedItem={ownerType}
        />
        <TextFieldContainer>
          <OwnerSelection
            type={ownerType}
            ownerId={ownerId}
            onChange={setOwnerId}
          />
        </TextFieldContainer>
      </Row>
      <Divider />
      <Row>
        <Dropdown
          id="resource-type-dropdown"
          label={t("selectResourceType")}
          titleText={t("resourceType")}
          items={resourceTypeItems}
          onChange={(item: { selectedItem: ResourceType }) =>
            setResourceType(item.selectedItem)
          }
          itemToString={(item: string) => (item ? t(item) : "")}
          selectedItem={
            resourceTypeItems.find((item) => item === resourceType) || ""
          }
        />
        <TextFieldContainer>
          <TextField
            label={t("resourceId")}
            placeholder={t("enterId")}
            onChange={setResourceId}
            value={resourceId}
            autoFocus
          />
        </TextFieldContainer>
      </Row>
      <Divider />
      <CheckboxGroup
        legendText={
          <PermissionsSectionLabel>
            <Translate i18nKey="selectPermission">
              Select at least one permission. Visit{" "}
              <DocumentationLink path="" withIcon>
                authorizations
              </DocumentationLink>{" "}
              for a full overview.
            </Translate>
          </PermissionsSectionLabel>
        }
      >
        {resourcePermissions[resourceType].map((permission) => (
          <Checkbox
            key={permission}
            labelText={PermissionType[permission]}
            id={permission}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleChangeCheckbox(e.target.checked, permission)
            }
          />
        ))}
      </CheckboxGroup>
    </FormModal>
  );
};

export default AddModal;
