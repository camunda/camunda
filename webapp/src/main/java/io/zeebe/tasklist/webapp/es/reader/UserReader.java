package io.zeebe.tasklist.webapp.es.reader;

import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.UserEntity;
import io.zeebe.tasklist.es.schema.indices.UserIndex;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

@Component
public class UserReader {

  private static final Logger logger = LoggerFactory.getLogger(UserReader.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private UserIndex userIndex;

  @Autowired
  private ObjectMapper objectMapper;

  //TODO #45
  public UserEntity getUser(String username) {

    //TODO query only needed fields
    final ConstantScoreQueryBuilder esQuery = constantScoreQuery(idsQuery(username));

    final SearchRequest searchRequest = new SearchRequest(userIndex.getAlias())
        .source(
            new SearchSourceBuilder()
                .query(esQuery)
                .fetchSource(new String[]{ UserIndex.USERNAME, UserIndex.FIRSTNAME, UserIndex.LASTNAME }, null));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
      } else {
        throw new NotFoundException(String.format("Could not find user with username '%s'.", username));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      logger.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  //TODO #47 this must live in UserStorage implementation
  public List<UserEntity> getUsersByUsernames(List<String> usernames) {

    //TODO query only needed fields
    final ConstantScoreQueryBuilder esQuery = constantScoreQuery(idsQuery().addIds(usernames.toArray(String[]::new)));

    //TODO #47 we need the results in same order as list of ids: script exapmles: https://gist.github.com/darklow/7132077
    final SearchRequest searchRequest = new SearchRequest(userIndex.getAlias())
        .source(
            new SearchSourceBuilder()
                .query(esQuery)
                .fetchSource(new String[]{ UserIndex.USERNAME, UserIndex.FIRSTNAME, UserIndex.LASTNAME }, null));

    try {
      final List<UserEntity> userEntities = ElasticsearchUtil.scroll(searchRequest, UserEntity.class, objectMapper, esClient);
      return userEntities;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      logger.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

}
