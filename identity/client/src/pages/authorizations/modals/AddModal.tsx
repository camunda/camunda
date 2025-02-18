/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership. Licensed under the Camunda License 1.0.
 * You may not use this file except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import { Dropdown, CheckboxGroup, Checkbox } from "@carbon/react";
import { useApiCall } from "src/utility/api";
import useTranslate from "src/utility/localization";
import { FormModal, UseEntityModalProps } from "src/components/modal";
import {
  Authorization,
  createAuthorization,
  OwnerType,
  PermissionTypes,
  ResourceType,
} from "src/utility/api/authorizations";
import { useNotifications } from "src/components/notifications";
import TextField from "src/components/form/TextField";
import { DocumentationLink } from "src/components/documentation";
import {
  Row,
  Divider,
  TextFieldContainer,
  PermissionsSectionLabel,
} from "./components";

type ResourcePermissionsType = {
  [key in keyof typeof ResourceType]: Authorization["permissionTypes"];
};

const resourcePermissions: ResourcePermissionsType = {
  APPLICATION: ["ACCESS"],
  AUTHORIZATION: ["UPDATE", "CREATE", "READ", "DELETE"],
  BATCH: ["CREATE", "READ", "DELETE"],
  DECISION_DEFINITION: [
    "DELETE_DECISION_INSTANCE",
    "CREATE_DECISION_INSTANCE",
    "READ_DECISION_INSTANCE",
    "READ_DECISION_DEFINITION",
  ],
  DECISION_REQUIREMENTS_DEFINITION: ["UPDATE", "READ", "DELETE"],
  RESOURCE: ["CREATE", "DELETE_DRD", "DELETE_PROCESS", "DELETE_FORM"],
  GROUP: ["UPDATE", "CREATE", "READ", "DELETE"],
  MAPPING_RULE: ["UPDATE", "CREATE", "READ", "DELETE"],
  MESSAGE: ["CREATE", "READ"],
  PROCESS_DEFINITION: [
    "CREATE_PROCESS_INSTANCE",
    "DELETE_PROCESS_INSTANCE",
    "UPDATE_USER_TASK",
    "READ_PROCESS_INSTANCE",
    "READ_USER_TASK",
    "UPDATE_PROCESS_INSTANCE",
    "READ_PROCESS_DEFINITION",
  ],
  ROLE: ["UPDATE", "CREATE", "READ", "DELETE"],
  SYSTEM: ["UPDATE", "READ"],
  TENANT: ["UPDATE", "CREATE", "READ", "DELETE"],
  USER: ["UPDATE", "CREATE", "READ", "DELETE"],
};

const AddModal: FC<UseEntityModalProps<ResourceType>> = ({
  open,
  onClose,
  onSuccess,
  entity: defaultResourceType,
}) => {
  const { t, Translate } = useTranslate("authorizations");
  const { enqueueNotification } = useNotifications();
  const [apiCall, { loading }] = useApiCall(createAuthorization);
  const [ownerType, setOwnerType] = useState<OwnerType>(OwnerType.USER);
  const [ownerId, setOwnerId] = useState("");
  const [resourceId, setResourceId] = useState("");
  const [resourceType, setResourceType] =
    useState<ResourceType>(defaultResourceType);
  const [permissionTypes, setPermissionTypes] = useState<
    Authorization["permissionTypes"]
  >([]);

  const ownerTypeItems = Object.values(OwnerType);
  const resourceTypeItems = Object.values(ResourceType);

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
      submitDisabled={loading}
      confirmLabel={t("Create authorization")}
      onSubmit={handleSubmit}
    >
      <Row>
        <Dropdown
          id="owner-type-dropdown"
          label="Select Owner type"
          titleText="Owner type"
          items={ownerTypeItems}
          onChange={(item: { selectedItem: OwnerType }) =>
            setOwnerType(item.selectedItem)
          }
          itemToString={(item: Authorization["ownerType"]) =>
            item ? t(OwnerType[item]) : ""
          }
          selectedItem={ownerType}
        />
        <TextFieldContainer>
          <TextField
            label={t("Owner ID")}
            placeholder={t("Enter ID")}
            onChange={setOwnerId}
            value={ownerId}
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
            label={t("Resource ID")}
            placeholder={t("Enter ID")}
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
            <Translate>Select at least one permission. Visit</Translate>{" "}
            <DocumentationLink
              path="/concepts/resource-authorizations/"
              withIcon
            >
              {t("Resource permissions")}
            </DocumentationLink>{" "}
            <Translate>for a full overview.</Translate>
          </PermissionsSectionLabel>
        }
      >
        {resourcePermissions[resourceType].map((permission) => (
          <Checkbox
            key={permission}
            labelText={t(permission)}
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
