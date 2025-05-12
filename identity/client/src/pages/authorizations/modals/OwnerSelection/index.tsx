/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { OwnerType } from "src/utility/api/authorizations";
import { searchUser } from "src/utility/api/users";
import { searchGroups } from "src/utility/api/groups";
import { searchMappingRule } from "src/utility/api/mappings";
import { searchRoles } from "src/utility/api/roles";
import OwnerSelection from "./OwnerSelection";

type SelectionProps = {
  type: OwnerType;
  onChange: (newOwner: string) => void;
};

const Selection: FC<SelectionProps> = ({ type, onChange }) => {
  switch (type) {
    case OwnerType.USER:
      return (
        <OwnerSelection
          id="userSelection"
          onChange={onChange}
          searchFn={searchUser}
          getId={(user) => user.username}
          itemToString={(user) => user.name || user.username}
        />
      );
    case OwnerType.GROUP:
      return (
        <OwnerSelection
          id="groupSelection"
          onChange={onChange}
          searchFn={searchGroups}
          getId={(group) => group.groupKey}
          itemToString={(group) => group.name || group.groupKey}
        />
      );
    case OwnerType.MAPPING:
      return (
        <OwnerSelection
          id="mappingSelection"
          onChange={onChange}
          searchFn={searchMappingRule}
          getId={(mapping) => mapping.mappingRuleId}
          itemToString={(mapping) => mapping.name || mapping.mappingRuleId}
        />
      );
    case OwnerType.ROLE:
      return (
        <OwnerSelection
          id="roleSelection"
          onChange={onChange}
          searchFn={searchRoles}
          getId={(role) => role.roleKey}
          itemToString={(role) => role.name || role.roleKey}
        />
      );
    default:
      return null;
  }
};

export default Selection;
