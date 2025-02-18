import { ApiDefinition, apiPost, pathBuilder } from "src/utility/api/request";
import { USERS_ENDPOINT } from "src/utility/api/users/index";
import { UserAuthorization } from "src/utility/api/authorizations";
import { SearchResponse } from "src/utility/api";

export type GetUserAuthorizationsParams = { key: number };

const path = pathBuilder(USERS_ENDPOINT);

export const getUserAuthorizations: ApiDefinition<
  SearchResponse<UserAuthorization>,
  GetUserAuthorizationsParams
> = ({ key }) => apiPost(path(key, "authorizations", "search"), {});
