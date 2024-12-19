/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useEffect, useState } from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import { Dropdown, Tag } from "@carbon/react";
import styled from "styled-components";
import {
  Authorization,
  PatchAuthorizationAction,
  patchAuthorizations,
  PermissionType,
} from "src/utility/api/authorizations";
import { ApiDefinition } from "src/utility/api/request";
import useDebounce from "react-debounced";

const SelectedResources = styled.div`
  margin-top: 0;
`;

export enum ResourceType {
  AUTHORIZATION = "AUTHORIZATION",
  MAPPING_RULE = "MAPPING_RULE",
  MESSAGE = "MESSAGE",
  BATCH = "BATCH",
  APPLICATION = "APPLICATION",
  SYSTEM = "SYSTEM",
  TENANT = "TENANT",
  DEPLOYMENT = "DEPLOYMENT",
  PROCESS_DEFINITION = "PROCESS_DEFINITION",
  DECISION_REQUIREMENTS_DEFINITION = "DECISION_REQUIREMENTS_DEFINITION",
  DECISION_DEFINITION = "DECISION_DEFINITION",
  GROUP = "GROUP",
  USER = "USER",
  ROLE = "ROLE",
}

export type PatchAuthorizationUIParams = {
  ownerKey: number;
  resourceType: string;
  permissionType: PermissionType;
  addedResourceIds: string[];
  removedResourceIds: string[];
};

export type PatchAuthorizationModalParams = {
  ownerKey: number;
  resourceType: ResourceType | null;
  permissionType: PermissionType | null;
  currentAuthorizations: Authorization[];
};

const findAvailableResourceIds = (
  resourceType: string | null,
  permissionType: PermissionType | null,
  currentAuthorizations: Authorization[],
) => {
  if (resourceType && resourceType != "") {
    if (permissionType) {
      return currentAuthorizations
        .filter((a) => a.resourceType == resourceType)
        .flatMap((a) => a.permissions)
        .filter((p) => p.permissionType == permissionType)
        .flatMap((p) => p.resourceIds);
    }
  }
  return [];
};

const PatchModal: FC<UseEntityModalProps<PatchAuthorizationModalParams>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate();

  const [resourceType, setResourceType] = useState(entity.resourceType);
  const [permissionType, setPermissionType] = useState(entity.permissionType);
  const [selectedResources, setSelectedResources] = useState<string[]>(
    findAvailableResourceIds(
      entity.resourceType,
      entity.permissionType,
      entity.currentAuthorizations,
    ),
  );
  const [resourceId, setResourceId] = useState<string>("");

  const findAddedAndRemoveResourceIds = () => {
    const currentResourceIds = findAvailableResourceIds(
      resourceType,
      permissionType,
      entity.currentAuthorizations,
    );
    const removedResourceIds = currentResourceIds.filter(
      (item) => !selectedResources.includes(item),
    );
    const addedResourceIds = selectedResources.filter(
      (item) => !currentResourceIds.includes(item),
    );
    return [addedResourceIds, removedResourceIds];
  };

  const patchRemovedAuthorization: ApiDefinition<
    undefined,
    PatchAuthorizationUIParams
  > = (params) => {
    return patchAuthorizations({
      ownerKey: params.ownerKey,
      action: PatchAuthorizationAction.REMOVE,
      resourceType: params.resourceType,
      permissions: [
        {
          permissionType: params.permissionType,
          resourceIds: params.removedResourceIds,
        },
      ],
    });
  };

  const patchAddedAuthorization: ApiDefinition<
    undefined,
    PatchAuthorizationUIParams
  > = (params) => {
    return patchAuthorizations({
      ownerKey: params.ownerKey,
      action: PatchAuthorizationAction.ADD,
      resourceType: params.resourceType,
      permissions: [
        {
          permissionType: params.permissionType,
          resourceIds: params.addedResourceIds,
        },
      ],
    });
  };

  const [patchRemovedAuthorizationCall] = useApiCall(patchRemovedAuthorization);
  const [patchAddedAuthorizationCall, { loading }] = useApiCall(
    patchAddedAuthorization,
  );

  const handleSubmit = async () => {
    const [addedResourceIds, removedResourceIds] =
      findAddedAndRemoveResourceIds();
    if (
      removedResourceIds.length == 0 ||
      (await patchRemovedAuthorizationCall({
        ownerKey: entity.ownerKey,
        resourceType: resourceType!,
        permissionType: permissionType!,
        addedResourceIds: addedResourceIds,
        removedResourceIds: removedResourceIds,
      }))
    ) {
      if (
        addedResourceIds.length == 0 ||
        (await patchAddedAuthorizationCall({
          ownerKey: entity.ownerKey,
          resourceType: resourceType!,
          permissionType: permissionType!,
          addedResourceIds: addedResourceIds,
          removedResourceIds: removedResourceIds,
        }))
      ) {
        onSuccess();
      }
    }
  };

  const resourceTypeItems = Object.values(ResourceType);

  const permissionTypeItems = Object.values(PermissionType);

  const onSelectResource = (resource: string) => {
    setSelectedResources([...selectedResources, resource]);
  };

  const onUnselectResource = (resource: string) => () => {
    setSelectedResources(selectedResources.filter((r) => resource !== r));
  };

  useEffect(() => {
    setSelectedResources(
      findAvailableResourceIds(
        resourceType,
        permissionType,
        entity.currentAuthorizations,
      ),
    );
  }, [resourceType, permissionType, entity.currentAuthorizations]);

  const debounce = useDebounce();

  return (
    <FormModal
      open={open}
      headline={t("Edit Authorizations")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Updating authorizations")}
      confirmLabel={t("Update authorizations")}
      submitDisabled={!resourceType && !permissionType}
    >
      <Dropdown
        id="resource-type-dropdown"
        label="Select Resource type"
        titleText="Select Resource type"
        items={resourceTypeItems}
        onChange={(item: { selectedItem: ResourceType }) =>
          setResourceType(item.selectedItem)
        }
        itemToString={(item: ResourceType) => (item ? t(item) : "")}
        selectedItem={
          resourceTypeItems.find((item) => item === resourceType) || ""
        }
      />

      <Dropdown
        id="permission-type-dropdown"
        label="Select Permission type"
        titleText="Select Permission type"
        onChange={(item: { selectedItem: PermissionType }) =>
          setPermissionType(item.selectedItem)
        }
        items={permissionTypeItems}
        itemToString={(item: PermissionType) => (item ? t(item) : "")}
        selectedItem={
          permissionTypeItems.find((item) => item === permissionType) || ""
        }
      />

      <SelectedResources>
        {selectedResources.map((resource) => (
          <Tag
            key={resource}
            onClose={onUnselectResource(resource)}
            size="md"
            type="blue"
            filter
          >
            {resource}
          </Tag>
        ))}
      </SelectedResources>
      <TextField
        label={t("Resource ID")}
        placeholder={t("Resource ID")}
        onChange={(newValue: string) => {
          setResourceId(newValue);
          debounce(() => {
            onSelectResource(newValue);
            setResourceId("");
          });
        }}
        autoFocus
        value={resourceId}
      />
    </FormModal>
  );
};

export default PatchModal;
