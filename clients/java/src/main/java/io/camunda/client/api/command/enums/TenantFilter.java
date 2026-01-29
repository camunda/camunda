/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.api.command.enums;

import io.camunda.client.protocol.rest.TenantFilterEnum;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.List;

/**
 * Protocol agnostic tenant filter. See {@link
 * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter} and {@link
 * io.camunda.client.protocol.rest.TenantFilterEnum}.
 *
 * <p>Used only during job activation.
 */
public enum TenantFilter {
  /**
   * When set, the tenants associated with the client are resolved dynamically right when the job is
   * activated. This means you don't need to know in advance the tenants you want, and can control
   * it dynamically entirely from the admin control panel.
   */
  ASSIGNED {
    @Override
    public GatewayOuterClass.TenantFilter toGrpc() {
      return GatewayOuterClass.TenantFilter.ASSIGNED;
    }

    @Override
    public TenantFilterEnum toRest() {
      return TenantFilterEnum.ASSIGNED;
    }
  },
  /**
   * When set, the command expects an additional {@link
   * io.camunda.client.api.command.CommandWithOneOrMoreTenantsStep#tenantIds(List)} to be set, and
   * only jobs associated with the specific tenants will be activated.
   */
  PROVIDED {
    @Override
    public GatewayOuterClass.TenantFilter toGrpc() {
      return GatewayOuterClass.TenantFilter.PROVIDED;
    }

    @Override
    public TenantFilterEnum toRest() {
      return TenantFilterEnum.PROVIDED;
    }
  };

  public abstract GatewayOuterClass.TenantFilter toGrpc();

  public abstract TenantFilterEnum toRest();
}
