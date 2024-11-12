import {ApiDefinition, apiDelete, apiPatch, apiPost} from "../request";
import {SearchResponse} from "src/utility/api";
import {EntityData} from "src/components/entityList/EntityList";

export const USERS_ENDPOINT = "/users";

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
  const {key, name, email, username, password} = user;
  return apiPatch(`${USERS_ENDPOINT}/${key}`, {
    changeset: {name, email, username, password: password ?? ""}
  });
};

type DeleteUserParams = {
  key: number;
};

export const deleteUser: ApiDefinition<undefined, DeleteUserParams> = ({
  key,
}) => apiDelete(`${USERS_ENDPOINT}/${key}`);
