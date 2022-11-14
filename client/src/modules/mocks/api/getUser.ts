/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {UserDto} from 'modules/api/getUser';
import {mockGetRequest} from './mockRequest';

const mockGetUser = () => mockGetRequest<UserDto>('/api/authentications/user');

export {mockGetUser};
