import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  pathBuilder,
} from "src/utility/api/request";
import { USERS_ENDPOINT } from "src/utility/api/users/index";
import { Role } from "src/utility/api/roles";
import {
  AssignRoleParams,
  UnassignRoleParams,
} from "src/utility/api/roles/assign";

export type GetUserRolesParams = { id: string };

const path = pathBuilder(USERS_ENDPOINT);

export const getUserRoles: ApiDefinition<Role[], GetUserRolesParams> = ({
  id,
}) => apiGet(path(id, "roles"));

export const assignUserRole: ApiDefinition<undefined, AssignRoleParams> = ({
  id,
  roleId,
}) => apiPost(path(id, "roles"), { roleId: roleId });

export const removeUserRole: ApiDefinition<undefined, UnassignRoleParams> = ({
  id,
  roleId,
}) => apiDelete(path(id, "roles", roleId));
