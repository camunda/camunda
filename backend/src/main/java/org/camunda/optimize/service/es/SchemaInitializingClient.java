package org.camunda.optimize.service.es;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.FilterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper client that performs schema initialization before any action towards ES is
 * executed. Please note that this client cannot be used for schema initialization mechanism
 * itself as it will cause infinite loop.
 *
 * @author Askar Akhmerov
 */
public class SchemaInitializingClient extends FilterClient {
  private final Logger logger = LoggerFactory.getLogger(SchemaInitializingClient.class);
  private ElasticSearchSchemaInitializer elasticSearchSchemaInitializer;

  public SchemaInitializingClient(Client in) {
    super(in);
  }

  @Override
  protected <
      Request extends ActionRequest,
      Response extends ActionResponse,
      RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>
      >
  void doExecute(
      Action<Request, Response, RequestBuilder> action,
      Request request,
      ActionListener<Response> listener
  ) {

    elasticSearchSchemaInitializer.initializeSchema();

    if (logger.isTraceEnabled()) {
      logger.trace(request.toString());
    }

    super.doExecute(action, request, listener);
  }

  public ElasticSearchSchemaInitializer getElasticSearchSchemaInitializer() {
    return elasticSearchSchemaInitializer;
  }

  public void setElasticSearchSchemaInitializer(ElasticSearchSchemaInitializer elasticSearchSchemaInitializer) {
    this.elasticSearchSchemaInitializer = elasticSearchSchemaInitializer;
  }
}
