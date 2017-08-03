package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class QueryFilterAdder {

  @Autowired
  private DateFilter dateFilter;

  @Autowired
  private VariableFilter variableFilter;

  @Autowired
  private ExecutedFlowNodeFilter executedFlowNodeFilter;

  private List<QueryFilter> queryFilterList;

  @PostConstruct
  private void init() {
    queryFilterList = new ArrayList<>();
    queryFilterList.add(dateFilter);
    queryFilterList.add(variableFilter);
    queryFilterList.add(executedFlowNodeFilter);
  }

  public void addFilterToQuery(BoolQueryBuilder query, FilterMapDto filter) {
    if (filter != null) {
      for (QueryFilter queryFilter : queryFilterList) {
        queryFilter.addFilters(query, filter);
      }
    }
  }


}
