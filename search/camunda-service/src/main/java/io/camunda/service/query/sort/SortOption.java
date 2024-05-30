package io.camunda.service.query.sort;

import static io.camunda.data.clients.sort.DataStoreSortOptionsBuilders.reverseOrder;
import static io.camunda.data.clients.sort.DataStoreSortOptionsBuilders.sortOptions;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.sort.SortOrder;
import java.util.List;

public interface SortOption {

  List<DataStoreSortOptions> toSortOptions(final boolean reverse);

  public final record FieldSorting(String field, SortOrder order) {

    DataStoreSortOptions toSortOption(final boolean reverse) {
      if (!reverse) {
        return sortOptions(field, order, "_last");
      } else {
        return sortOptions(field, reverseOrder(order), "_first");
      }
    }

  }
}
