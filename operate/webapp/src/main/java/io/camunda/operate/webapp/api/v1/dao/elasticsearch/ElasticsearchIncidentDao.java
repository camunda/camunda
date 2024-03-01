/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.IncidentDao;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchIncidentDaoV1")
public class ElasticsearchIncidentDao extends ElasticsearchDao<Incident> implements IncidentDao {

  @Autowired private IncidentTemplate incidentIndex;

  @Override
  protected void buildFiltering(
      final Query<Incident> query, final SearchSourceBuilder searchSourceBuilder) {
    final Incident filter = query.getFilter();
    List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(Incident.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(Incident.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(
          buildTermQuery(Incident.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      queryBuilders.add(buildTermQuery(Incident.TYPE, filter.getType()));
      queryBuilders.add(buildMatchQuery(Incident.MESSAGE, filter.getMessage()));
      queryBuilders.add(buildTermQuery(Incident.STATE, filter.getState()));
      queryBuilders.add(buildTermQuery(Incident.JOB_KEY, filter.getJobKey()));
      queryBuilders.add(buildTermQuery(Incident.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildMatchDateQuery(Incident.CREATION_TIME, filter.getCreationTime()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
  }

  @Override
  public Incident byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    List<Incident> incidents;
    try {
      incidents = searchFor(new SearchSourceBuilder().query(termQuery(IncidentTemplate.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading incident for key %s", key), e);
    }
    if (incidents.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No incident found for key %s ", key));
    }
    if (incidents.size() > 1) {
      throw new ServerException(String.format("Found more than one incidents for key %s", key));
    }
    return incidents.get(0);
  }

  @Override
  public Results<Incident> search(final Query<Incident> query) throws APIException {
    logger.debug("search {}", query);
    mapFieldsInSort(query);
    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, Incident.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(incidentIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<Incident> incidents =
            ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToIncident);
        return new Results<Incident>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(incidents)
            .setSortValues(sortValues);
      } else {
        return new Results<Incident>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  private void mapFieldsInSort(final Query<Incident> query) {
    if (query.getSort() == null) {
      return;
    }
    query.setSort(
        query.getSort().stream()
            .map(
                s ->
                    s.setField(
                        Incident.OBJECT_TO_SEARCH_MAP.getOrDefault(s.getField(), s.getField())))
            .collect(Collectors.toList()));
  }

  protected Incident searchHitToIncident(SearchHit searchHit) {
    final Map<String, Object> searchHitAsMap = searchHit.getSourceAsMap();
    return new Incident()
        .setKey((Long) searchHitAsMap.get(IncidentTemplate.KEY))
        .setProcessInstanceKey((Long) searchHitAsMap.get(IncidentTemplate.PROCESS_INSTANCE_KEY))
        .setProcessDefinitionKey((Long) searchHitAsMap.get(IncidentTemplate.PROCESS_DEFINITION_KEY))
        .setType((String) searchHitAsMap.get(IncidentTemplate.ERROR_TYPE))
        .setMessage((String) searchHitAsMap.get(IncidentTemplate.ERROR_MSG))
        .setCreationTime(
            dateTimeFormatter.convertGeneralToApiDateTime(
                (String) searchHitAsMap.get(Incident.CREATION_TIME)))
        .setState((String) searchHitAsMap.get(Incident.STATE))
        .setJobKey((Long) searchHitAsMap.get(Incident.JOB_KEY))
        .setTenantId((String) searchHitAsMap.get(Incident.TENANT_ID));
  }

  protected List<Incident> searchFor(final SearchSourceBuilder searchSourceBuilder) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(incidentIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        return ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToIncident);
      } else {
        return List.of();
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }
}
