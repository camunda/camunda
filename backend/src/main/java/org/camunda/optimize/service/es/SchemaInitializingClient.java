package org.camunda.optimize.service.es;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.FilterClient;

/**
 * @author Askar Akhmerov
 */
public class SchemaInitializingClient extends FilterClient {
  private ElasticSearchSchemaInitializer elasticSearchSchemaInitializer;

  public SchemaInitializingClient(Client in) {
    super(in);
  }

  @Override
  protected <Request extends ActionRequest, Response extends ActionResponse, RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> void doExecute(Action<Request, Response, RequestBuilder> action, Request request, ActionListener<Response> listener) {
    elasticSearchSchemaInitializer.initializeSchema();
    super.doExecute(action, request, listener);
  }

  public ElasticSearchSchemaInitializer getElasticSearchSchemaInitializer() {
    return elasticSearchSchemaInitializer;
  }

  public void setElasticSearchSchemaInitializer(ElasticSearchSchemaInitializer elasticSearchSchemaInitializer) {
    this.elasticSearchSchemaInitializer = elasticSearchSchemaInitializer;
  }
}
