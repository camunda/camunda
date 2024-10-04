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
package io.camunda.common.exception;

public class SdkException extends RuntimeException {

<<<<<<< HEAD:spring-boot-starter-camunda-sdk/src/main/java/io/camunda/common/exception/SdkException.java
  private static final long serialVersionUID = 1L;
=======
public class ApiProperties {
  private Boolean enabled;
  private URL baseUrl;
  private String audience;
  private String scope;
>>>>>>> 63375132 (feat: expose oauth scope config in spring sdk):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/common/ApiProperties.java

  public SdkException(final Throwable cause) {
    super(cause);
  }

  public SdkException(final String message) {
    super(message);
  }

  public SdkException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }
}
