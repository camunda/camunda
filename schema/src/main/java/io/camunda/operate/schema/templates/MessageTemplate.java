/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;
import org.springframework.stereotype.Component;

@Component
public class MessageTemplate extends AbstractTemplateDescriptor implements Prio3Backup {

  public static final String INDEX_NAME = "message";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String MESSAGE_NAME = "messageName";
  public static final String CORRELATION_KEY = "correlationKey";
  public static final String PUBLISH_DATE = "publishDate";
  public static final String EXPIRE_DATE = "expireDate";
  public static final String DEADLINE = "deadline";
  public static final String TIME_TO_LIVE = "timeToLive";
  public static final String MESSAGE_ID = "messageId";
  public static final String VARIABLES = "variables";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.5.0";
  }
}
