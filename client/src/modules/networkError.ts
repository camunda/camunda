/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

class NetworkError extends Error {
  status: Response['status'] | void = undefined;

  constructor(message: string, response: Response) {
    super(message);

    this.status = response.status;
  }
}

export {NetworkError};
