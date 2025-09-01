package io.camunda.search.clients.aggregator;

import java.util.List;
import java.util.Objects;

public record SearchCardinalityAggregator(
    String name, String field, List<SearchAggregator> aggregations) implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder> {
    private String field;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public SearchCardinalityAggregator build() {
      return new SearchCardinalityAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."), field, aggregations);
    }
  }
}
