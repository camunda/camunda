import { ApiDefinition, apiDelete, apiPost, apiPut } from "../request";
import { SearchResponse } from "src/utility/api";
import { EntityData } from "src/components/entityList/EntityList";

export const USERS_ENDPOINT = "/users";

export type UserKeys = "key" | "name" | "username" | "password" | "email";

export type User = EntityData & {
  key: number;
  name: string;
  username: string;
  password: string;
  email: string;
};

export const searchUser: ApiDefinition<SearchResponse<User>> = () =>
  apiPost(`${USERS_ENDPOINT}/search`);

type GetUserParams = {
  username: string;
};

export const getUserDetails: ApiDefinition<
  SearchResponse<User>,
  GetUserParams
> = ({ username }) =>
  apiPost(`${USERS_ENDPOINT}/search`, { filter: { username } });

type CreateUserParams = Omit<User, "key">;

export const createUser: ApiDefinition<undefined, CreateUserParams> = (user) =>
  apiPost(USERS_ENDPOINT, { ...user, enabled: true });

export const updateUser: ApiDefinition<undefined, User> = (user) => {
  const { name, email, username, password } = user;
  return apiPut(`${USERS_ENDPOINT}/${username}`, {
    name,
    email,
    password: password ?? "",
  });
};

type DeleteUserParams = {
  username: string;
};

export const deleteUser: ApiDefinition<undefined, DeleteUserParams> = ({
  username,
}) => apiDelete(`${USERS_ENDPOINT}/${username}`);
