/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {FC, useEffect, useState} from "react";
import TextField from "src/components/form/TextField";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import {Dropdown, Tag} from "@carbon/react";
import styled from "styled-components";
import {
  PatchAuthorizationParams,
  patchAuthorizations
} from "src/utility/api/authorizations";

const SelectedResources = styled.div`
  margin-top: 0;
`;

const PatchModal: FC<UseEntityModalProps<PatchAuthorizationParams>> = ({
  open,
  onClose,
  onSuccess,
  entity,
}) => {
  const { t } = useTranslate();
  const [apiCall, { loading, namedErrors }] = useApiCall(patchAuthorizations);
  const [resourceType, setResourceType] = useState("");
  const [action, setAction] = useState("");
  const [permissionType, setPermissionType] = useState("");


  const handleSubmit = async () => {
    const { success } = await apiCall({
      ownerKey: entity.ownerKey,
      action: action,
      resourceType: resourceType,
      permissions: [{permissionType: permissionType, resourceIds: ["1"]}]
    });

    if (success) {
      onSuccess();
    }
  };

  const actionTypeItems = [
    { id: "ADD", text: "ADD"},
    { id: "REMOVE", text: "REMOVE"}
  ]
    const resourceTypeItems = [
        { id: 'AUTHORIZATION', text: 'AUTHORIZATION' },
        { id: 'MAPPING_RULE', text: 'MAPPING_RULE' },
        { id: 'MESSAGE', text: 'MESSAGE' },
        { id: 'BATCH', text: 'BATCH' },
        { id: 'APPLICATION', text: 'APPLICATION' },
        { id: 'SYSTEM', text: 'SYSTEM' },
        { id: 'TENANT', text: 'TENANT' },
        { id: 'DEPLOYMENT', text: 'DEPLOYMENT' },
        { id: 'PROCESS_DEFINITION', text: 'PROCESS_DEFINITION' },
        { id: 'DECISION_REQUIREMENTS_DEFINITION', text: 'DECISION_REQUIREMENTS_DEFINITION' },
        { id: 'DECISION_DEFINITION', text: 'DECISION_DEFINITION' },
        { id: 'GROUP', text: 'GROUP' },
        { id: 'USER', text: 'USER' },
        { id: 'ROLE', text: 'ROLE' },
    ];

    const permissionTypeItems = [
        { id: 'CREATE', text: 'CREATE' },
        { id: 'READ', text: 'READ' },
        { id: 'READ_INSTANCE', text: 'READ_INSTANCE' },
        { id: 'READ_USER_TASK', text: 'READ_USER_TASK' },
        { id: 'UPDATE', text: 'UPDATE' },
        { id: 'DELETE', text: 'DELETE' },
        { id: 'DELETE_PROCESS', text: 'DELETE_PROCESS' },
        { id: 'DELETE_DRD', text: 'DELETE_DRD' },
        { id: 'DELETE_FORM', text: 'DELETE_FORM' }
    ];

    const [selectedResources, setSelectedResources] = useState<string[]>([]);

   const onSelectResource = (resource: string) => {
        setSelectedResources([...selectedResources, resource]);
    };

    const onUnselectResource =
        (resource: string) =>
            () => {
                setSelectedResources(selectedResources.filter((r) => resource !== r));
            };

    useEffect(() => {
        if (open) {
            setSelectedResources([]);
        }
    }, [open]);

  return (
    <FormModal
      open={open}
      headline={t("Edit Authorizations")}
      onClose={onClose}
      onSubmit={handleSubmit}
      loading={loading}
      loadingDescription={t("Updating authorizations")}
      confirmLabel={t("Update authorizations")}
    >
        <Dropdown
            id="resource-type-dropdown"
            label="Select Resource type"
            items={resourceTypeItems}
            onChange={({ selectedItem }) => setResourceType(selectedItem.id)}
            itemToString={(item) => (item ? item.text : '')}
        />

      <Dropdown
          id="resource-type-dropdown"
          label="Select Action"
          onChange={({ selectedItem }) => setAction(selectedItem.id)}
          items={actionTypeItems}
          itemToString={(item) => (item ? item.text : '')}
      />

        <Dropdown
            id="permission-type-dropdown"
            label="Select Permission type"
            onChange={({ selectedItem }) => setPermissionType(selectedItem.id)}
            items={permissionTypeItems}
            itemToString={(item) => (item ? item.text : '')}
        />

        <SelectedResources>
            {selectedResources.map((r) => (
                <Tag
                    key={r}
                    onClose={onUnselectResource(r)}
                    size="md"
                    type="blue"
                    filter
                >
                    {r}
                </Tag>
            ))}
        </SelectedResources>
        <TextField
            label={t("Resource ID")}
            placeholder={t("Resource ID")}
            errors={namedErrors?.name}
            onChange={onSelectResource}
            autoFocus
        />

    </FormModal>
  );
};

export default PatchModal;
