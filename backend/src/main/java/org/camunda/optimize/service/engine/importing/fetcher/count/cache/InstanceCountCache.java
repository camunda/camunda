package org.camunda.optimize.service.engine.importing.fetcher.count.cache;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InstanceCountCache {

  private Map<String, Long> cache = new HashMap<>();

  public boolean isEmpty() {
    return cache.isEmpty();
  }

  public int getEntrySize() {
    return cache.size();
  }

  public void addCount(String processDefinitionId, long count) {
    cache.put(processDefinitionId, count);
  }

  public boolean hasCount(String processDefinitionId) {
    return cache.containsKey(processDefinitionId);
  }

  public long getCount(String processDefinitionId) {
    return cache.getOrDefault(processDefinitionId, 0L);
  }

  public long getTotalCount() {
    return cache.values().stream().reduce((c1,c2) -> c1+c2).orElse(0L);
  }

  public void reset() {
    cache.clear();
  }
}
