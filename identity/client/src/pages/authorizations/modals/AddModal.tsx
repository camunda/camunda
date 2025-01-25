/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership. Licensed under the Camunda License 1.0.
 * You may not use this file except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import { Dropdown } from "@carbon/react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseModalProps } from "src/components/modal";
import { createAuthorization } from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";
import {
  Row,
  Divider,
  TextFieldContainer,
  PermissionsSectionLabel,
} from "./components";
import TextField from "src/components/form/TextField";
import { ResourceType } from "src/pages/users/detail/authorization/PatchModal";
import { DocumentationLink } from "src/components/documentation";
import { CheckboxGroup } from "@carbon/react";
import { Checkbox } from "@carbon/react";

export enum OwnerType {
  USER = "User",
}

const AddAuthorizationModal: FC<UseModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslate("authorizations");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading, namedErrors }] = useApiCall(createAuthorization);
  const [ownerType, setOwnerType] = useState("");
  const [ownerId, setOwnerId] = useState("");
  const [resourceId, setResourceId] = useState("");
  const [resourceType, setResourceType] = useState("");
  const [permissions, setPermissions] = useState<string[]>([]);

  const ownerTypeItems = Object.values(OwnerType);
  const resourceTypeItems = Object.values(ResourceType);
  const submitDisabled = loading;

  const handleChangeCheckbox = (checked: boolean, id: string) => {
    if (checked) {
      setPermissions([...permissions, id]);
    } else {
      setPermissions(permissions.filter((p) => p !== id));
    }
  };

  // @TODO: correct some of the values (casing on some enum values) to get request to work
  const handleSubmit = async () => {
    const { success } = await apiCall({
      ownerType,
      ownerId,
      resourceId,
      resourceType,
      permissions,
    });

    if (success) {
      enqueueNotification({
        kind: "success",
        title: t("Authorization created"),
        subtitle: t(
          "You have successfully created authorization {{ resourceId }}",
          {
            resourceId,
          },
        ),
      });
      onSuccess();
    }
  };

  return (
    <FormModal
      headline={t("Create authorization")}
      open={open}
      onClose={onClose}
      loading={loading}
      submitDisabled={submitDisabled}
      confirmLabel={t("Create authorization")}
      onSubmit={handleSubmit}
    >
      <Row>
        <Dropdown
          id="owner-type-dropdown"
          label="Select Owner type"
          titleText="Owner type"
          items={ownerTypeItems.map((i) => t(i))}
          onChange={(item: { selectedItem: string }) =>
            setOwnerType(item.selectedItem)
          }
          itemToString={(item: string) => (item ? t(item) : "")}
          selectedItem={ownerTypeItems.find((item) => item === ownerType) || ""}
        />
        <TextFieldContainer>
          <TextField
            label={t("Owner ID")}
            placeholder={t("Enter ID")}
            onChange={setOwnerId}
            value={ownerId}
            errors={namedErrors?.ownerId}
            autoFocus
          />
        </TextFieldContainer>
      </Row>
      <Divider />
      <Row>
        <Dropdown
          id="resource-type-dropdown"
          label="Select Resource type"
          titleText="Resource type"
          items={resourceTypeItems}
          onChange={(item: { selectedItem: string }) =>
            setResourceType(item.selectedItem)
          }
          itemToString={(item: string) => (item ? t(item) : "")}
          selectedItem={
            resourceTypeItems.find((item) => item === resourceType) || ""
          }
        />
        <TextFieldContainer>
          <TextField
            label={t("Resource ID")}
            placeholder={t("Enter ID")}
            onChange={setResourceId}
            value={resourceId}
            errors={namedErrors?.resourceId}
            autoFocus
          />
        </TextFieldContainer>
      </Row>
      <Divider />
      <PermissionsSectionLabel>
        Select at least one permission. Visit{" "}
        <DocumentationLink path="" withIcon>
          {t("process definition permissions")}
        </DocumentationLink>{" "}
        for a full overview.
      </PermissionsSectionLabel>
      <CheckboxGroup>
        <Checkbox
          labelText={`Read process definition`}
          id="read-process-definition"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "read-process-definition")
          }
        />
        <Checkbox
          labelText={`Read process instance`}
          id="read-process-instance"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "read-process-instance")
          }
        />
        <Checkbox
          labelText={`Read user task`}
          id="read-user-task"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "read-user-task")
          }
        />
        <Checkbox
          labelText={`Read document`}
          id="read-document"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "read-document")
          }
        />
        <Checkbox
          labelText={`Update process instance`}
          id="update-process-instance"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "update-process-instance")
          }
        />
        <Checkbox
          labelText={`Update user task`}
          id="update-user-task"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "update-user-task")
          }
        />
        <Checkbox
          labelText={`Create process instance`}
          id="create-process-instance"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "create-process-instance")
          }
        />
        <Checkbox
          labelText={`Delete process instance`}
          id="delete-process-instance"
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            handleChangeCheckbox(e.target.checked, "delete-process-instance")
          }
        />
      </CheckboxGroup>
    </FormModal>
  );
};

export default AddAuthorizationModal;
