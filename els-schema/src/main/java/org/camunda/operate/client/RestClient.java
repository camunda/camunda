package org.camunda.operate.client;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// TODO: Add methods for create els-schema
// TODO: Add methods for doing operation actions
@Component
public class RestClient {

  @Autowired
  RestHighLevelClient esClient;
  
  protected boolean createIndices() {
    return false;
  }
  
  protected boolean createTemplates() {
    return false;
  }
  
  public boolean createSchema() {
    return createTemplates() && createIndices();
  }
}
