package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedReportCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.get.GetResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static org.camunda.optimize.service.es.reader.CollectionReader.EVERYTHING_ELSE_COLLECTION_ID;
import static org.camunda.optimize.service.es.reader.CollectionReader.EVERYTHING_ELSE_COLLECTION_NAME;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class CollectionHandlingIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void collectionIsWrittenToElasticsearch() {
    // given
    String id = createNewCollection();

    // then
    GetResponse response =
      elasticSearchRule.getClient()
        .prepareGet(
          getOptimizeIndexAliasForType(COLLECTION_TYPE),
          COLLECTION_TYPE,
          id
        )
        .get();

    // then
    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = createNewCollection();

    // when
    List<ResolvedReportCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections, is(notNullValue()));
    assertThat(collections.size(), is(2));
    // the first collection is the one we created
    assertThat(collections.get(0).getId(), is(id));
    // the second collection is the everything else collection
    assertThat(collections.get(1).getId(), is(EVERYTHING_ELSE_COLLECTION_ID));
  }

  @Test
  public void noCollectionAvailableReturnsJustTheEverythingElseCollection() {
    // given
    String reportId = createNewSingleReport();

    // when
    List<ResolvedReportCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections, is(notNullValue()));
    assertThat(collections.size(), is(1));
    ResolvedReportCollectionDefinitionDto everythingElseCollection = collections.get(0);
    assertThat(everythingElseCollection.getId(), is(EVERYTHING_ELSE_COLLECTION_ID));
    assertThat(everythingElseCollection.getName(), is(EVERYTHING_ELSE_COLLECTION_NAME));
    assertThat(everythingElseCollection.getLastModified(), notNullValue());
    CollectionDataDto<ReportDefinitionDto> collectionData = collections.get(0).getData();
    assertThat(collectionData.getEntities().size(), is(1));
    assertThat(collectionData.getEntities().get(0).getId(), is(reportId));
  }

  @Test
  public void cantDeleteEverythingElseCollection() {
    // when
    Response response = deleteCollection(EVERYTHING_ELSE_COLLECTION_ID);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantUpdateEverythingElseCollection() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateCollectionRequest(EVERYTHING_ELSE_COLLECTION_ID, new SimpleCollectionDefinitionDto())
      .execute();

    // given
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void updateCollection() {
    // given
    String id = createNewCollection();
    String reportId = createNewSingleReport();

    CollectionDataDto<String> collectionData = new CollectionDataDto<>();
    Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    collectionData.setConfiguration(configuration);
    collectionData.setEntities(Collections.singletonList(reportId));

    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();
    collection.setData(collectionData);
    collection.setId("shouldNotBeUpdated");
    collection.setLastModifier("shouldNotBeUpdatedManually");
    collection.setName("MyCollection");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    collection.setCreated(shouldBeIgnoredDate);
    collection.setLastModified(shouldBeIgnoredDate);
    collection.setOwner("NewOwner");

    // when
    updateCollection(id, collection);
    List<ResolvedReportCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(2));
    ResolvedReportCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(storedCollection.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getOwner(), is("NewOwner"));
    CollectionDataDto<ReportDefinitionDto> resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);
    assertThat(resultCollectionData.getEntities().size(), is(1));
    assertThat(resultCollectionData.getEntities().get(0).getId(), is(reportId));
  }

  @Test
  public void updateCollectionWithReportIdThatDoesNotExists() {
    // given
    String id = createNewCollection();

    CollectionDataDto<String> collectionData = new CollectionDataDto<>();
    collectionData.setEntities(Collections.singletonList("fooReportId"));

    SimpleCollectionDefinitionDto collectionUpdate = new SimpleCollectionDefinitionDto();
    collectionUpdate.setData(collectionData);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateCollectionRequest(id, collectionUpdate)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void doNotUpdateNullFieldsInCollection() {
    // given
    String id = createNewCollection();
    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();

    // when
    updateCollection(id, collection);
    List<ResolvedReportCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(2));
    ResolvedReportCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getCreated(), is(notNullValue()));
    assertThat(storedCollection.getLastModified(), is(notNullValue()));
    assertThat(storedCollection.getLastModifier(), is(notNullValue()));
    assertThat(storedCollection.getName(), is(notNullValue()));
    assertThat(storedCollection.getOwner(), is(notNullValue()));
  }

  @Test
  public void resultListIsSortedByName() {
    // given
    String id1 = createNewCollection();
    String id2 = createNewCollection();

    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();
    collection.setName("B_collection");
    updateCollection(id1, collection);
    collection.setName("A_collection");
    updateCollection(id2, collection);

    // when
    List<ResolvedReportCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(3));
    assertThat(collections.get(0).getId(), is(id2));
    assertThat(collections.get(1).getId(), is(id1));
    assertThat(collections.get(2).getId(), is(EVERYTHING_ELSE_COLLECTION_ID));
  }

  @Test
  public void deletedReportsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String singleReportIdToDelete = createNewSingleReport();
    String combinedReportIdToDelete = createNewCombinedReport();

    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();
    CollectionDataDto<String> collectionData = new CollectionDataDto<>();
    collectionData.setConfiguration("");
    collectionData.setEntities(Arrays.asList(singleReportIdToDelete, combinedReportIdToDelete));
    collection.setData(collectionData);
    updateCollection(collectionId, collection);

    // when
    deleteReport(singleReportIdToDelete, true);
    deleteReport(combinedReportIdToDelete, true);

    // then
    List<ResolvedReportCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(2));
    assertThat(allResolvedCollections.get(0).getData().getEntities().size(), is(0));
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewCombinedReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response deleteCollection(String id) {
    return embeddedOptimizeRule.getRequestExecutor()
      .buildDeleteCollectionRequest(id)
      .execute();
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewCollection() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateCollection(String id, SimpleCollectionDefinitionDto updatedCollection) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateCollectionRequest(id, updatedCollection)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();
  }

  private List<ResolvedReportCollectionDefinitionDto> getAllResolvedCollections() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllCollectionsRequest()
      .executeAndReturnList(ResolvedReportCollectionDefinitionDto.class, 200);
  }

}
