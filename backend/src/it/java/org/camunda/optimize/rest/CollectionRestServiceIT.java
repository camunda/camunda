package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedReportCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.service.es.reader.CollectionReader.EVERYTHING_ELSE_COLLECTION_ID;
import static org.camunda.optimize.service.es.reader.CollectionReader.EVERYTHING_ELSE_COLLECTION_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class CollectionRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildCreateCollectionRequest()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewCollection() {
    // when
    IdDto idDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateCollectionRequest()
            .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void updateCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildUpdateCollectionRequest("1", null)
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateCollection() {
    //given
    String id = addEmptyCollectionToOptimize();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateCollectionRequest(id, new SimpleCollectionDefinitionDto())
            .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getAllCollectionsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetAllCollectionsRequest()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getAllCollections() {
    // when
    List<ResolvedReportCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then by default there is the everything else collection
    assertThat(collections.size(), is(1));
    assertThat(collections.get(0).getId(), is(EVERYTHING_ELSE_COLLECTION_ID));
    assertThat(collections.get(0).getName(), is(EVERYTHING_ELSE_COLLECTION_NAME));
  }
  @Test
  public void getCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetCollectionRequest("asdf")
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCollection() {
    //given
    String id = addEmptyCollectionToOptimize();

    // when
    SimpleCollectionDefinitionDto collection = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetCollectionRequest(id)
            .execute(SimpleCollectionDefinitionDto.class, 200);

    // then
    assertThat(collection, is(notNullValue()));
    assertThat(collection.getId(), is(id));
  }

  @Test
  public void getCollectionForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetCollectionRequest("fooid")
            .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Collection does not exist!"), is(true));
  }

  @Test
  public void deleteCollectionWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildDeleteCollectionRequest("1124")
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewCollection() {
    //given
    String id = addEmptyCollectionToOptimize();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteCollectionRequest(id)
            .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    // Only the everything else collection should be there
    assertThat(getAllResolvedCollections().size(), is(1));
  }

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateCollectionRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private List<ResolvedReportCollectionDefinitionDto> getAllResolvedCollections() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllCollectionsRequest()
            .executeAndReturnList(ResolvedReportCollectionDefinitionDto.class, 200);
  }
}
