package io.camunda.service.query;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

public class SpecificationBuilder<T> {

  public Specification<T> build(final List<Filter> filters) {
    return (root, query, criteriaBuilder) -> {
      final List<Predicate> predicates = new ArrayList<>();

      for (final Filter filter : filters) {
        final Predicate predicate = createPredicate(filter, root, criteriaBuilder);
        if (predicate != null) {
          predicates.add(predicate);
        }
      }

      // Explicitly define the type of the array passed to 'and' method
      final Predicate[] predicatesArray = predicates.toArray(new Predicate[0]);
      return criteriaBuilder.and(predicatesArray);
    };
  }

  private Predicate createPredicate(final Filter filter, final Root<T> root, final CriteriaBuilder criteriaBuilder) {
    switch (filter.getOperator()) {
      case "$eq":
        return criteriaBuilder.equal(root.get(filter.getField()), filter.getValue());
      case "$neq":
        return criteriaBuilder.notEqual(root.get(filter.getField()), filter.getValue());
      case "$gt":
        return criteriaBuilder.greaterThan(root.get(filter.getField()), (Comparable) filter.getValue());
      case "$gte":
        return criteriaBuilder.greaterThanOrEqualTo(root.get(filter.getField()), (Comparable) filter.getValue());
      case "$lt":
        return criteriaBuilder.lessThan(root.get(filter.getField()), (Comparable) filter.getValue());
      case "$lte":
        return criteriaBuilder.lessThanOrEqualTo(root.get(filter.getField()), (Comparable) filter.getValue());
      case "$in":
        return root.get(filter.getField()).in((List<?>) filter.getValue());
      case "$exists":
        return (Boolean) filter.getValue() ? criteriaBuilder.isNotNull(root.get(filter.getField())) : criteriaBuilder.isNull(root.get(filter.getField()));
      case "$or":
        return handleOrPredicate(filter, root, criteriaBuilder);
      default:
        throw new IllegalArgumentException("Unknown operator: " + filter.getOperator());
    }
  }

  private Predicate handleOrPredicate(final Filter filter, final Root<T> root, final CriteriaBuilder criteriaBuilder) {
    final List<Predicate> orPredicates = new ArrayList<>();

    for (final Filter orFilter : filter.getOrFilters()) {
      final Predicate predicate = createPredicate(orFilter, root, criteriaBuilder);
      if (predicate != null) {
        orPredicates.add(predicate);
      }
    }

    return criteriaBuilder.or(orPredicates.toArray(new Predicate[0]));
  }
}
