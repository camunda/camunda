package io.zeebe.tasklist.webapp.graphql;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import graphql.kickstart.execution.context.DefaultGraphQLContext;
import graphql.kickstart.execution.context.GraphQLContext;
import graphql.kickstart.servlet.context.DefaultGraphQLServletContext;
import graphql.kickstart.servlet.context.DefaultGraphQLWebSocketContext;
import graphql.kickstart.servlet.context.GraphQLServletContextBuilder;
import io.zeebe.tasklist.webapp.es.reader.UserReader;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;

@Component
public class TasklistGraphQLContextBuilder implements GraphQLServletContextBuilder {

  public static final String USER_DATA_LOADER = "userDataLoader";

  @Autowired
  private UserReader userReader;

  @Override
  public GraphQLContext build(HttpServletRequest req, HttpServletResponse response) {
    return DefaultGraphQLServletContext.createServletContext(buildDataLoaderRegistry(), null).with(req).with(response)
        .build();
  }

  @Override
  public GraphQLContext build() {
    return new DefaultGraphQLContext(buildDataLoaderRegistry(), null);
  }

  @Override
  public GraphQLContext build(Session session, HandshakeRequest request) {
    return DefaultGraphQLWebSocketContext.createWebSocketContext(buildDataLoaderRegistry(), null).with(session)
        .with(request).build();
  }

  private DataLoaderRegistry buildDataLoaderRegistry() {
    DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
    dataLoaderRegistry.register(USER_DATA_LOADER,
        new DataLoader<String, UserDTO>(usernames ->
            CompletableFuture.supplyAsync(() ->
                UserDTO.createFrom(userReader.getUsersByUsernames(usernames)))));
    return dataLoaderRegistry;
  }
}
