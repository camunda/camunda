package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UserType extends StrictTypeMappingCreator {

  public static final String USER_ID = "id";
  public static final String PASSWORD = "password";
  public static final String LAST_LOGGED_IN = "lastLoggedIn";
  public static final String CREATED_AT = "createdAt";
  public static final String CREATED_BY = "createdBy";
  public static final String LAST_MODIFIER = "lastModifier";
  public static final String LAST_MODIFIED = "lastModified";

  public static final String PERMISSIONS = "permissions";

  public static final String READ_ONLY = "readOnly";
  public static final String CAN_SHARE_PUBLICLY = "canSharePublicly";
  public static final String HAS_ADMIN_RIGHTS = "hasAdminRights";

  public static final String PROCESS_DEFINITIONS = "processDefinitions";
  public static final String USE_WHITE_LIST = "useWhiteList";
  public static final String DEFINITION_ID_LIST = "idList";

  @Override
  public String getType() {
    return configurationService.getElasticSearchUsersType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(USER_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PASSWORD)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_LOGGED_IN)
        .field("type", "keyword")
      .endObject()
      .startObject(CREATED_AT)
        .field("type", "keyword")
      .endObject()
      .startObject(CREATED_BY)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "keyword")
      .endObject()
      .startObject(PERMISSIONS)
        .field("type", "nested")
        .startObject("properties");
          addNestedPermissionsField(newBuilder)
        .endObject()
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedPermissionsField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(PROCESS_DEFINITIONS)
        .field("type", "nested")
        .startObject("properties");
          addNestedProcessDefinitionsField(newBuilder)
        .endObject()
      .endObject()
      .startObject(READ_ONLY)
        .field("type", "boolean")
      .endObject()
      .startObject(CAN_SHARE_PUBLICLY)
        .field("type", "boolean")
      .endObject()
      .startObject(HAS_ADMIN_RIGHTS)
        .field("type", "boolean")
      .endObject();
    return newBuilder;
  }
  private XContentBuilder addNestedProcessDefinitionsField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(USE_WHITE_LIST)
        .field("type", "boolean")
      .endObject()
      .startObject(DEFINITION_ID_LIST)
        .field("type", "keyword")
      .endObject();
  }

}
