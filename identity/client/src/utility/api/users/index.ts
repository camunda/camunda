import { ApiDefinition, apiDelete, apiPost, apiPut } from "../request";
import { SearchResponse } from "src/utility/api";

export const USERS_ENDPOINT = "/users";

export type User = {
  id: number;
  key: number;
  name: string;
  username: string;
  password: string;
  email: string;
  enabled: boolean;
};

export const searchUser: ApiDefinition<SearchResponse<User>> = () =>
  apiPost(`${USERS_ENDPOINT}/search`);

type GetUserParams = {
  id: string;
};

export const getUserDetails: ApiDefinition<SearchResponse<User>, GetUserParams> = ({ id }) =>
  apiPost(`${USERS_ENDPOINT}/search`, {filter: {username: id}});

type CreateUserParams = Omit<User, "id" | "enabled">;

export const createUser: ApiDefinition<undefined, CreateUserParams> = (user) =>
  apiPost(USERS_ENDPOINT, { ...user, enabled: true });

type UpdateUserParams = Omit<User, "enabled">;

export const updateUser: ApiDefinition<undefined, UpdateUserParams> = (user) =>
  apiPut(`${USERS_ENDPOINT}/${user.id}`, { ...user, enabled: true });

type DeleteUserParams = GetUserParams;

export const deleteUser: ApiDefinition<undefined, DeleteUserParams> = ({
  id,
}) => apiDelete(`${USERS_ENDPOINT}/${id}`);
