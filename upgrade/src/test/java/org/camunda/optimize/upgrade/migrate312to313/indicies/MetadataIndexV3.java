/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate312to313.indicies;

import java.io.IOException;
import org.camunda.optimize.service.db.schema.index.MetadataIndex;
import org.elasticsearch.xcontent.XContentBuilder;

/*
 This file should be removed after 3.13, it is only used in upgrading 3.12 -> 3.13
*/
public class MetadataIndexV3 extends MetadataIndex<XContentBuilder> {

  public static final int VERSION = 3;

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
        .startObject(SCHEMA_VERSION)
        .field("type", "keyword")
        .endObject()
        .startObject(INSTALLATION_ID)
        .field("type", "keyword")
        .endObject();
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
